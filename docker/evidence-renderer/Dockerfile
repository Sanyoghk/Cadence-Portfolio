FROM mcr.microsoft.com/playwright/python:v1.63.0-noble@sha256:72bd171a9ffc2b4b59532aaa6210e21014d07093120dc25528870c0b840da1f0
USER root
COPY requirements.txt /tmp/renderer-requirements.txt
RUN apt-get update && apt-get upgrade --no-install-recommends -y \
    && apt-get install --no-install-recommends -y poppler-utils util-linux \
    # Chromium-only PDF/screenshot runtime does not use WebKit's vulnerable GStreamer codecs.
    && apt-get purge -y gstreamer1.0-plugins-bad libgstreamer-plugins-bad1.0-0 \
    && rm -rf /var/lib/apt/lists/* \
    && pip install --no-cache-dir -r /tmp/renderer-requirements.txt \
    # The upstream development image also contains Poetry/virtualenv and their HTTP dependencies.
    # Retain only the four pinned renderer distributions, never unused vulnerable build tooling.
    && python3 -c 'import importlib.metadata as m, subprocess, sys; keep={"pip","playwright","pyee","greenlet","typing-extensions"}; remove=sorted({d.metadata["Name"] for d in m.distributions() if d.metadata["Name"].lower().replace("_","-") not in keep}); subprocess.run([sys.executable,"-m","pip","uninstall","--yes",*remove],check=True) if remove else None' \
    && python3 -m pip check && python3 -m pip uninstall --yes pip \
    # Upstream virtualenv leaves an executable cached pip wheel with its own stale vendored SBOM.
    # Delete those installer bytes as well; the vulnerability scanner remains unfiltered.
    && rm -rf /root/.cache/virtualenv \
    && python3 -c 'import importlib.metadata as m; assert {d.metadata["Name"].lower().replace("_","-") for d in m.distributions()} == {"playwright","pyee","greenlet","typing-extensions"}' \
    && useradd --create-home --uid 10001 evidence
WORKDIR /opt/evidence-renderer
COPY server.py child.py test_server.py smoke.py namespace_probe.py test_probe.py ephemeral.py test_ephemeral.py ./
RUN chmod 0555 /opt/evidence-renderer && chmod 0444 *.py
USER 10001:10001
ENV PYTHONDONTWRITEBYTECODE=1 TZ=UTC HOME=/tmp/evidence-home
EXPOSE 8081
ENTRYPOINT ["python3", "-I", "/opt/evidence-renderer/server.py"]
