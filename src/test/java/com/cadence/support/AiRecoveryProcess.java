package com.cadence.support;

import com.cadence.CadenceApplication;
import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.application.services.*;
import com.cadence.domain.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

/** Test-classpath-only child JVM. No fixture entry point is packaged in the application. */
public class AiRecoveryProcess {
  static String phase;
  static boolean content;
  static Instant fixtureTime;

  @TestConfiguration
  public static class Fixture {
    @Bean
    @Primary
    Clock recoveryClock() {
      return new Clock() {
        public ZoneId getZone() {
          return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
          return this;
        }

        public Instant instant() {
          return fixtureTime == null ? Instant.now() : fixtureTime;
        }
      };
    }

    @Bean
    @Primary
    BrandAnalysisPort recoveryPort(
        @org.springframework.beans.factory.annotation.Qualifier("brandAnalysisPort")
            BrandAnalysisPort delegate) {
      return command -> {
        if (!content && phase.equals("dispatch")) Runtime.getRuntime().halt(82);
        return delegate.analyze(command);
      };
    }
  }

  @TestConfiguration
  public static class ContentFixture {
    @Bean
    @Primary
    ContentGenerationService recoveryContent(
        @org.springframework.beans.factory.annotation.Qualifier("contentGenerationService")
            ContentGenerationService delegate) {
      return command -> {
        if (content && phase.equals("dispatch")) Runtime.getRuntime().halt(82);
        return delegate.generate(command);
      };
    }
  }

  public static void main(String[] args) throws Exception {
    phase = args[0];
    content = args.length > 2 && args[2].equals("CONTENT");
    Path state = Path.of(args[1]);
    try (var context =
        new SpringApplication(CadenceApplication.class, Fixture.class, ContentFixture.class)
            .run(
                "--spring.profiles.active=test,postgres-test",
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--cadence.ai-jobs.worker-enabled=false")) {
      var service = context.getBean(AiGenerationService.class);
      var jdbc = context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
      if (phase.equals("recover")) {
        var lines = Files.readAllLines(state);
        UUID operation = UUID.fromString(lines.get(0));
        fixtureTime = Instant.parse(lines.get(1)).plusSeconds(21);
        service.execute(service.claim(operation));
        var o =
            context
                .getBean(AiGenerationOperationRepository.class)
                .findById(operation)
                .orElseThrow();
        Files.writeString(state.resolveSibling("result"), o.getStatus().name());
        return;
      }
      var workspace =
          context
              .getBean(WorkspaceRepository.class)
              .saveAndFlush(new Workspace("Crash fixture", "Creator"));
      var brand =
          context
              .getBean(BrandRepository.class)
              .saveAndFlush(new Brand("Crash fixture", workspace));
      var actor =
          context
              .getBean(CadenceUserRepository.class)
              .saveAndFlush(
                  new CadenceUser("crash-" + UUID.randomUUID(), UserRole.OWNER, Set.of(workspace)));
      fixtureTime = Instant.now().plusMillis(1).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
      AiGenerationService.View view;
      if (content) {
        var scope = Set.of(workspace.getId());
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .setAuthentication(
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                    .authenticated(
                        new com.cadence.security.CadencePrincipal(
                            actor.getId(), "crash-test", UserRole.OWNER, scope),
                        null,
                        List.of(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_OWNER"))));
        var identities =
            context.getBean(com.cadence.application.usecases.BrandIdentityUseCase.class);
        var identity = identities.create(brand.getId(), scope, StrategyFixtures.identityContent());
        fixtureTime = Instant.now();
        identities.approve(identity.id(), scope, 0, actor.getId());
        var strategyJob =
            service.submit(
                actor.getId(),
                scope,
                brand.getId(),
                AiGenerationOperation.Kind.STRATEGY,
                0,
                "crash-strategy");
        service.execute(service.claim(strategyJob.operationId()));
        var sourceId = service.status(actor.getId(), scope, strategyJob.jobId()).resultId();
        if (sourceId == null) throw new AssertionError("Strategy fixture unavailable");
        fixtureTime = Instant.now();
        var approved =
            context
                .getBean(com.cadence.application.usecases.ManageStrategyUseCase.class)
                .approve(sourceId, scope, 0, actor.getId());
        fixtureTime = Instant.now().plusMillis(1).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        view =
            service.submitContent(
                actor.getId(),
                scope,
                brand.getId(),
                0,
                "crash",
                new ContentGenerationRequest(
                    sourceId,
                    approved.lockVersion(),
                    List.of(
                        approved
                            .playbook()
                            .fourWeekOutline()
                            .getFirst()
                            .slots()
                            .getFirst()
                            .slotId()),
                    null,
                    null,
                    null,
                    ContentGenerationRequest.Field.ALL));
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
      } else
        view =
            service.submit(
                actor.getId(),
                Set.of(workspace.getId()),
                brand.getId(),
                AiGenerationOperation.Kind.DECLARED,
                0,
                "crash");
      Files.writeString(state, view.operationId() + "\n" + fixtureTime + "\n" + view.jobId());
      var ticket = service.claim(view.operationId());
      if (phase.equals("claim")) Runtime.getRuntime().halt(81);
      if (phase.equals("publication")) {
        jdbc.execute(
            "create function pause_test_publication() returns trigger language plpgsql as $$ begin if NEW.status='SUCCEEDED' then perform pg_sleep(120);end if;return NEW;end $$");
        jdbc.execute(
            "create trigger pause_test_publication before update on ai_generation_operations for each row execute function pause_test_publication()");
      }
      service.execute(ticket);
      if (!service
          .status(actor.getId(), Set.of(workspace.getId()), view.jobId())
          .status()
          .equals("SUCCEEDED")) throw new AssertionError("Expected success before crash");
      Runtime.getRuntime().halt(83);
    }
  }
}
