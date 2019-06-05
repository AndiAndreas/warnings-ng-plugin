package io.jenkins.plugins.analysis.warnings.recorder;

import org.jenkinsci.test.acceptance.docker.DockerFixture;
import org.jenkinsci.test.acceptance.docker.fixtures.SshdContainer;

/**
 * Docker Container which sets up make + gcc.
 */
@DockerFixture(
        id = "gcc",
        ports = {22, 8080}
)
class GccContainer extends SshdContainer {
}