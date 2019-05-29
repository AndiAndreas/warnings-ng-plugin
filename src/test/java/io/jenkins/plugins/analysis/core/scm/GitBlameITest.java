package io.jenkins.plugins.analysis.core.scm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;

import com.gargoylesoftware.htmlunit.html.HtmlPage;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.impl.RelativeTargetDirectory;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.api.SCMHead;

import io.jenkins.plugins.analysis.core.model.AnalysisResult;
import io.jenkins.plugins.analysis.core.steps.IssuesRecorder;
import io.jenkins.plugins.analysis.core.testutil.IntegrationTestWithJenkinsPerSuite;
import io.jenkins.plugins.analysis.warnings.Doxygen;
import io.jenkins.plugins.analysis.warnings.Java;
import io.jenkins.plugins.analysis.warnings.recorder.pageobj.SourceControlRow;
import io.jenkins.plugins.analysis.warnings.recorder.pageobj.SourceControlTable;

import static io.jenkins.plugins.analysis.core.assertions.Assertions.*;

import static org.assertj.core.api.Assertions.*;

public class GitBlameITest extends IntegrationTestWithJenkinsPerSuite {

    @Rule
    public GitSampleRepoRule gitRepo = new GitSampleRepoRule();

    @Test
    public void shouldShowGitBlameHistory() throws Exception {
        final FreeStyleProject project = createJavaWarningsFreestyleProject();
        final String fileName = "Hello.java";

        createRepositoryInProject(project);
        appendTextToFileInRepo(gitRepo, fileName, "public class HelloWorld {\n"
                + "       public static void main (String[] args) {\n"
                + "             System.out.println(\"Hello World!\");\n"
                + "       }}", "John Doe", "John@localhost");
        //createJavaWarningInRepository(gitRepo, fileName, 4, "Some Error");
        appendTextToFileInRepo(gitRepo, fileName, "{", "Jane Doe", "Jane@localhost");
        createJavaWarningInRepository(gitRepo, fileName, 4, "Unexpected end of File");

        AnalysisResult analysisResult = scheduleBuildAndAssertStatus(project, Result.SUCCESS);
        assertThat(analysisResult).hasTotalSize(1);

        HtmlPage detailsPage = getDetailsWebPage(project, analysisResult);
        SourceControlTable sourceControlTable = new SourceControlTable(detailsPage);

        List<SourceControlRow> sourceControlRows = sourceControlTable.getRows();
        assertThat(sourceControlRows.size()).isEqualTo(2);cd 
        assertThat(sourceControlRows.get(0).getValue(SourceControlRow.AUTHOR)).isEqualTo("John Doe");
        assertThat(sourceControlRows.get(0).getValue(SourceControlRow.EMAIL)).isEqualTo("hans@hamburg.com");
        assertThat(sourceControlRows.get(0).getValue(SourceControlRow.FILE)).isEqualTo(fileName + ":1");
        assertThat(sourceControlRows.get(0).getValue(SourceControlRow.DETAILS_CONTENT)).isEqualTo(
                "HelloWorld method opened");

        assertThat(sourceControlRows.get(1).getValue(SourceControlRow.AUTHOR)).isEqualTo("Jane Doe");
        assertThat(sourceControlRows.get(1).getValue(SourceControlRow.EMAIL)).isEqualTo("Jane@localhost");
        assertThat(sourceControlRows.get(1).getValue(SourceControlRow.FILE)).isEqualTo(fileName + ":2");
        assertThat(sourceControlRows.get(1).getValue(SourceControlRow.DETAILS_CONTENT)).isEqualTo(
                "HelloWorld method closed");
    }


    private void createRepositoryInProject(final FreeStyleProject project)
            throws Exception {
        gitRepo.init();
        gitRepo.git("checkout", "master");
        project.setScm(new GitSCM(gitRepo.fileUrl()));
    }

    private FreeStyleProject createJavaWarningsFreestyleProject() {
        FreeStyleProject project = createFreeStyleProject();
        Java java = new Java();
        java.setPattern("**/*.txt");
        enableWarnings(project, java);
        return project;
    }

    private void createJavaWarningInRepository(final GitSampleRepoRule repository, final String file,
            final int lineNumber, final String warningText)
            throws Exception {
        String warningsFile = "javac_warnings.txt";
        String warning = String.format("[WARNING] %s:[%d,42] [deprecation] %s\n", file, lineNumber, warningText);
        appendTextToFileInRepo(repository, warningsFile, warning, "dummy user", "dummy@user.de");
    }

    private FreeStyleProject createJavaWarningsFreestyleProject(final String pattern) {
        FreeStyleProject project = createFreeStyleProject();
        Java java = new Java();
        java.setPattern(pattern);
        enableWarnings(project, java);
        return project;
    }

    private HtmlPage getDetailsWebPage(final FreeStyleProject project, final AnalysisResult result) {
        int buildNumber = result.getBuild().getNumber();
        String pluginId = result.getId();
        System.out.println(String.format("%d/%s", buildNumber, pluginId));
        return getWebPage(JavaScriptSupport.JS_ENABLED, project, String.format("%d/%s", buildNumber, pluginId));
    }

    private void appendTextToFileInRepo(final GitSampleRepoRule repository, final String fileName, final String text,
            final String user, final String email) throws Exception {
        repository.git("config", "user.name", user);
        repository.git("config", "user.email", email);

        File file = new File(repository.getRoot(), fileName);
        try (FileWriter fileWriter = new FileWriter(file, true)) {
            fileWriter.write(text);
        }

        repository.git("add", fileName);
        repository.git("commit", "-m", String.format("Appended code to %s", fileName));
    }

    private void replaceLineOfFileInRepo(final GitSampleRepoRule repository, final String fileName,
            final int lineToReplace, final String text, final String user, final String email) throws Exception {
        repository.git("config", "user.name", user);
        repository.git("config", "user.email", email);

        try {
            Path filePath = new File(repository.getRoot(), fileName).toPath();
            List<String> stringList = Files.readAllLines(filePath);
            stringList.set(lineToReplace, text);

            try (FileWriter writer = new FileWriter("output.txt")) {
                for (String str : stringList) {
                    writer.write(str);
                }
            }
        }
        catch (IOException e) {
            System.out.println(String.format("Could not adapt file %s.", fileName));
        }

        repository.git("add", fileName);
        repository.git("commit", "-m", String.format("Adapted %s", fileName));
    }
}
