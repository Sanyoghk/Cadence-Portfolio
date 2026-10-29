package com.cadence.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cadence.application.repositories.WorkspaceRepository;
import com.cadence.domain.Workspace;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OptimisticLockingTest {

  @Autowired private WorkspaceRepository workspaces;

  @Autowired private EntityManagerFactory entityManagerFactory;

  @Test
  void shouldRejectAStaleConcurrentUpdate() {
    Workspace saved = workspaces.saveAndFlush(new Workspace("Cadence", "Marketing"));

    EntityManager firstManager = entityManagerFactory.createEntityManager();
    EntityManager secondManager = entityManagerFactory.createEntityManager();
    try {
      Workspace firstCopy = firstManager.find(Workspace.class, saved.getId());
      Workspace staleCopy = secondManager.find(Workspace.class, saved.getId());

      firstManager.getTransaction().begin();
      firstCopy.rename("Cadence One");
      firstManager.getTransaction().commit();

      secondManager.getTransaction().begin();
      staleCopy.rename("Cadence Two");
      assertThrows(RuntimeException.class, () -> secondManager.getTransaction().commit());

      assertEquals("Cadence One", workspaces.findById(saved.getId()).orElseThrow().getName());
    } finally {
      if (firstManager.getTransaction().isActive()) {
        firstManager.getTransaction().rollback();
      }
      if (secondManager.getTransaction().isActive()) {
        secondManager.getTransaction().rollback();
      }
      firstManager.close();
      secondManager.close();
    }
  }
}
