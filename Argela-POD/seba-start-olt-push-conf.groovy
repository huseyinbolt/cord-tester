node ("${podName}") {
    timeout (100) {
        try {
            stage ("Parse deployment configuration file") {
                sh returnStdout: true, script: "rm -rf ${configBaseDir}"
                sh returnStdout: true, script: "git clone https://github.com/huseyinbolt/pod-configs.git"
                deployment_config = readYaml file: "${configBaseDir}/${configDeploymentDir}/${configFileName}.yaml"
            }
            if ( params.configurePod ) {
                stage('Restart OLT processes') {
                    for(int i=0; i < deployment_config.olts.size(); i++) {
                        timeout(5) {
                            sh returnStdout: true, script: """
                        ssh-keyscan -H ${deployment_config.olts[i].ip} >> ~/.ssh/known_hosts
                        sshpass -p ${deployment_config.olts[i].pass} ssh -l ${deployment_config.olts[i].user} ${deployment_config.olts[i].ip} 'service bal_core_dist stop' || true
                        sshpass -p ${deployment_config.olts[i].pass} ssh -l ${deployment_config.olts[i].user} ${deployment_config.olts[i].ip} 'service openolt stop' || true
                        sshpass -p ${deployment_config.olts[i].pass} ssh -l ${deployment_config.olts[i].user} ${deployment_config.olts[i].ip} '> /var/log/bal_core_dist.log'
                        sshpass -p ${deployment_config.olts[i].pass} ssh -l ${deployment_config.olts[i].user} ${deployment_config.olts[i].ip} '> /var/log/openolt.log'
                        sshpass -p ${deployment_config.olts[i].pass} ssh -l ${deployment_config.olts[i].user} ${deployment_config.olts[i].ip} 'service bal_core_dist start &'
                        sleep 5
                        sshpass -p ${deployment_config.olts[i].pass} ssh -l ${deployment_config.olts[i].user} ${deployment_config.olts[i].ip} 'service openolt start &'
                        """
                        }
                        timeout(15) {
                            waitUntil {
                                onu_discovered = sh returnStdout: true, script: "sshpass -p ${deployment_config.olts[i].pass} ssh -l ${deployment_config.olts[i].user} ${deployment_config.olts[i].ip} 'cat /var/log/openolt.log | grep \"oper_state: up\" | wc -l'"
                                echo "ONU Discovered ${onu_discovered}"
                                return onu_discovered.toInteger() > 0
                            }
                        }
                    }
                }
                dir ("${configBaseDir}/${configToscaDir}/${profile}") {
                    stage('Configure R-CORD - Fabric and whitelist') {
                        timeout(1) {
                            waitUntil {
                                out_fabric = sh returnStdout: true, script: """
                            curl -s -H "xos-username:admin@opencord.org" -H "xos-password:letmein" -X POST --data-binary @${configFileName}-fabric.yaml http://${deployment_config.nodes[0].ip}:30007/run | grep -i "created models" | wc -l
                            """
                                return out_fabric.toInteger() == 1
                            }
                        }
                    }
                    stage('Configure R-CORD - Subscriber') {
                        timeout(1) {
                            waitUntil {
                                out_subscriber = sh returnStdout: true, script: """
                            curl -s -H 'xos-username:admin@opencord.org' -H 'xos-password:letmein' -X POST --data-binary @${configFileName}-subscribers.yaml http://${deployment_config.nodes[0].ip}:30007/run | grep -i "created models" | wc -l
                            """
                                return out_subscriber.toInteger() == 1
                            }
                        }
                    }
                    stage('Configure R-CORD - OLT') {
                        timeout(1) {
                            waitUntil {
                                out_olt = sh returnStdout: true, script: """
                            curl -H 'xos-username:admin@opencord.org' -H 'xos-password:letmein' -X POST --data-binary @${configFileName}-olt.yaml http://${deployment_config.nodes[0].ip}:30007/run | grep -i "created models" | wc -l
                            """
                                return out_olt.toInteger() == 1
                            }
                        }
                    }
                }
                stage('Configure Subscriber Acces') {
                    timeout(1) {
                        sh returnStdout: true, script: """
                        export KUBECONFIG=$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf
                        ssh-keyscan -p 30115 -H ${deployment_config.nodes[0].ip} >> ~/.ssh/known_hosts
                        sleep 30
                        sshpass -p rocks ssh -p 30115  onos@${deployment_config.nodes[0].ip} 'sr-xconnect-add of:0000000000000001 7 1 72'
                        sleep 2
                        sshpass -p rocks ssh -p 30115  onos@${deployment_config.nodes[0].ip} 'volt-add-subscriber-access of:00000000c0a81ffc 16'
                        sleep 10
                        """
                        return true
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