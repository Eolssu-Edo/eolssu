import hudson.model.Node
import hudson.slaves.DumbSlave
import hudson.slaves.JNLPLauncher
import hudson.slaves.RetentionStrategy
import jenkins.model.Jenkins

def controller = Jenkins.get()
def name = 'edo1-blog'
def remoteFS = '/var/lib/jenkins-blog-agent'
def agent = controller.getNode(name)

if (controller.getLabel(name).nodes.any { it.nodeName != name }) {
    throw new IllegalStateException('The edo1-blog label is already assigned to another node')
}

if (agent == null) {
    def launcher = new JNLPLauncher()
    launcher.setWebSocket(true)
    controller.addNode(new DumbSlave(
        name, 'Eolssu blog publication', remoteFS, '1', Node.Mode.EXCLUSIVE,
        '', launcher, new RetentionStrategy.Always(), []))
} else if (agent.remoteFS != remoteFS || agent.numExecutors != 1 ||
           agent.mode != Node.Mode.EXCLUSIVE || agent.labelString ||
           !(agent.launcher instanceof JNLPLauncher) || !agent.launcher.webSocket) {
    throw new IllegalStateException('Existing edo1-blog differs; refusing to overwrite')
}

println 'edo1-blog registered with one exclusive executor'
