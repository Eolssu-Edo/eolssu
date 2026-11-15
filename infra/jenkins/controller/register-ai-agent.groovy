import hudson.model.Node
import hudson.slaves.DumbSlave
import hudson.slaves.JNLPLauncher
import hudson.slaves.RetentionStrategy
import jenkins.model.Jenkins

// S15P31E102-244: the ai image build/test/deploy job gets its own slot so it neither waits for nor blocks
// the backend build on edo1-host. Deployments still serialize on the shared deployment lock.
def controller = Jenkins.get()
def name = 'edo1-ai'
def remoteFS = '/var/lib/jenkins-ai-agent'
def agent = controller.getNode(name)

if (controller.getLabel(name).nodes.any { it.nodeName != name }) {
    throw new IllegalStateException('The edo1-ai label is already assigned to another node')
}

if (agent == null) {
    def launcher = new JNLPLauncher()
    launcher.setWebSocket(true)
    controller.addNode(new DumbSlave(
        name, 'Eolssu ai image build and deployment', remoteFS, '1', Node.Mode.EXCLUSIVE,
        '', launcher, new RetentionStrategy.Always(), []))
} else if (agent.remoteFS != remoteFS || agent.numExecutors != 1 ||
           agent.mode != Node.Mode.EXCLUSIVE || agent.labelString ||
           !(agent.launcher instanceof JNLPLauncher) || !agent.launcher.webSocket) {
    throw new IllegalStateException('Existing edo1-ai differs; refusing to overwrite')
}

println 'edo1-ai registered with one exclusive executor'
