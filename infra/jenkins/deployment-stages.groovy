// Observe progress while a single process retains the shared deployment lock.
// transaction writes JSON phase events to log and returns its shell exit status.
def call(String log, Closure transaction) {
    def finished = log + '.finished'
    writeFile file: log, text: ''
    sh "rm -f -- '${finished}'"
    def exitStatus = 1
    def failedPhase = ''
    def phaseCount = 0
    parallel(
        transaction: {
            try {
                exitStatus = transaction()
            } finally {
                writeFile file: finished, text: 'finished'
            }
        },
        phases: {
            def cursor = 0
            def events = []
            while (true) {
                waitUntil(initialRecurrencePeriod: 250, quiet: true) {
                    def done = fileExists(finished)
                    events = readFile(log).readLines().findAll { it.contains('"event": "phase_') && it.endsWith('}') }
                    return events.size() > cursor || done
                }
                if (cursor >= events.size()) { break }
                def started = events[cursor++]
                if (!started.contains('"event": "phase_started"')) {
                    error('Invalid deployment progress sequence')
                }
                def name = started.replaceFirst('.*"phase": "([^"]+)".*', '$1')
                phaseCount++
                stage('Deploy: ' + name) {
                    def terminal = ''
                    waitUntil(initialRecurrencePeriod: 250, quiet: true) {
                        def done = fileExists(finished)
                        events = readFile(log).readLines().findAll { it.contains('"event": "phase_') && it.endsWith('}') }
                        if (events.size() > cursor) {
                            terminal = events[cursor++]
                            return true
                        }
                        return done
                    }
                    if (!terminal.contains('"event": "phase_completed"') || !terminal.contains('"phase": "' + name + '"')) {
                        if (!failedPhase) { failedPhase = name }
                        catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                            error('Deployment phase failed: ' + name)
                        }
                    }
                }
            }
        }
    )
    echo readFile(log)
    if (exitStatus != 0 || failedPhase || phaseCount == 0) {
        currentBuild.description = failedPhase ? 'Deployment failed: ' + failedPhase : 'Deployment failed before phase reporting'
        error(currentBuild.description)
    }
}
return this
