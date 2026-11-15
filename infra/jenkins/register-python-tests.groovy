import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob

// S15P31E102-945: create eolssu-python-tests from the installed managed Pipeline. Never overwrites a job.
// Run with the existing admin CLI `groovy =` after linting /opt/eolssu/config/python-tests.Jenkinsfile.
def name = 'eolssu-python-tests'
def source = new File('/opt/eolssu/config/python-tests.Jenkinsfile')
if (!source.isFile()) {
    throw new IllegalStateException('Install /opt/eolssu/config/python-tests.Jenkinsfile first')
}
def script = source.getText('UTF-8')
if (!script.contains('S15P31E102-945') || !script.contains("upstreamProjects: 'eolssu-backend'")) {
    throw new IllegalStateException('Unexpected Pipeline source; refusing to register')
}

def controller = Jenkins.get()
if (controller.getItem('eolssu-backend') == null) {
    throw new IllegalStateException('Upstream job eolssu-backend is missing')
}
def job = controller.getItem(name)
if (job != null) {
    if (job instanceof WorkflowJob && job.definition instanceof CpsFlowDefinition && job.definition.script == script) {
        println "${name} already matches the managed Pipeline"
        return
    }
    throw new IllegalStateException("${name} exists with a different definition; back up its config.xml and update it by hand")
}

job = controller.createProject(WorkflowJob, name)
job.description = 'S15P31E102-945: develop Python tests (infra·catalog·data·ai) chosen by changed paths; runs after eolssu-backend'
job.definition = new CpsFlowDefinition(script, true)
job.save()
println "${name} created; run Build Now once to register its parameters and triggers (the first run tests every group)"
