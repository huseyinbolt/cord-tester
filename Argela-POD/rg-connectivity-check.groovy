node ("${podName}") {
    timeout (100) {
        try {
            stage ("Parse deployment configuration file") {
                sh returnStdout: true, script: "rm -rf ${configBaseDir}"
                sh returnStdout: true, script: "git clone https://github.com/huseyinbolt/pod-configs.git"
                deployment_config = readYaml file: "${configBaseDir}/${configDeploymentDir}/${configFileName}.yaml"
            }
            stage('Check connectivity to Internet') {
                timeout(2) {
                    waitUntil {
                        four_google_ping_received = sh returnStdout: true, script: """
                            ssh-keyscan  -H 192.168.31.253 >> ~/.ssh/known_hosts
                            sshpass -p 1234 ssh -l argela 192.168.31.253 'ping -c 4 www.google.com | grep "4 received" | wc -l'
                            """
                        return four_google_ping_received.toInteger() == 1
                    }
                }
            }
            currentBuild.result = 'SUCCESS'
        } catch (err) {
            currentBuild.result = 'FAILURE'
        }
        echo "RESULT: ${currentBuild.result}"
    }
}