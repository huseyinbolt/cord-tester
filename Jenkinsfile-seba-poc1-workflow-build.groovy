// Copyright 2017-present Open Networking Foundation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

node ("${TestNodeName}") {
    timeout (100) {
        try {
            stage ("Parse deployment configuration file") {
                sh returnStdout: true, script: "rm -rf ${configBaseDir}"
                //sh returnStdout: true, script: "git clone -b ${branch} ${cordRepoUrl}/${configBaseDir}"
                sh returnStdout: true, script: "git clone https://github.com/huseyinbolt/pod-configs"
                deployment_config = readYaml file: "${configBaseDir}/${configDeploymentDir}/${configFileName}.yaml"
            }
            stage('Clean up') {
                timeout(10) {
                    sh returnStdout: true, script: """
                    rm -rf helm-charts cord-tester
                    git clone -b ${branch} ${cordRepoUrl}/helm-charts
                    export KUBECONFIG=$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf
                    for hchart in \$(helm list -q | grep -E -v 'docker-registry|mavenrepo|ponnet');
                    do
                        echo "Purging chart: \${hchart}"
                        helm delete --purge "\${hchart}"
                    done
                    """
                    timeout(5) {
                        waitUntil {
                            helm_deleted = sh returnStdout: true, script: """
                            export KUBECONFIG=$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf &&
                            helm ls -q | grep -E -v 'docker-registry|mavenrepo|ponnet' | wc -l
                            """
                            return helm_deleted.toInteger() == 0
                        }
                    }
                    timeout(5) {
                        waitUntil {
                            kubectl_deleted = sh returnStdout: true, script: """
                            export KUBECONFIG=$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf &&
                            kubectl get pods --all-namespaces --no-headers | grep -E -v 'kube-system|docker-registry|mavenrepo|ponnet' | wc -l
                            """
                            return kubectl_deleted.toInteger() == 0
                        }
                    }
                    // timeout(5) {
                    //     waitUntil {
                    //         kubectl_deleted = sh returnStdout: true, script: """
                    //         export KUBECONFIG=$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf &&
                    //         kubectl get statefulset --all-namespaces --no-headers | grep -E -v 'kube-system|docker-registry|mavenrepo|ponnet' | wc -l
                    //         """
                    //         return kubectl_deleted.toInteger() == 0
                    //     }
                    // }
                }
            }
            dir ("helm-charts") {
                stage('Install CORD Kafka') {
                    timeout(10) {
                        sh returnStdout: true, script: """
                        export KUBECONFIG=$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf
                        helm install -f examples/kafka-single.yaml --version 0.8.8 -n cord-kafka incubator/kafka
                        """
                    }
                    timeout(10) {
                        waitUntil {
                            kafka_instances_running = sh returnStdout: true, script: """
                            export KUBECONFIG=$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf &&
                            kubectl get pods | grep cord-kafka | grep -i running | grep 1/1 | wc -l
                            """
                            return kafka_instances_running.toInteger() == 2
                        }
                    }
                }
                // stage('Install Logging Infrastructure') {
                //     timeout(10) {
                //         sh returnStdout: true, script: """
                //         export KUBECONFIG=$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf
                //         helm dep update logging
                //         helm install -f examples/logging-single.yaml -n logging logging
                //         scripts/wait_for_pods.sh
                //         """
                //     }
                // }
                //stage('Install Monitoring Infrastructure') {
                //    timeout(10) {
                //        sh returnStdout: true, script: """
                //        export KUBECONFIG=$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf
                //        helm dep update nem-monitoring
                //        helm install -n nem-monitoring nem-monitoring
                //        scripts/wait_for_pods.sh
                //        """
                //    }
                //}
                stage('Install voltha') {
                    timeout(10) {
                        sh returnStdout: true, script: """
                        export KUBECONFIG=$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf
                        helm repo add incubator https://kubernetes-charts-incubator.storage.googleapis.com/
                        helm dep build voltha
                        helm install -n voltha -f ../${configBaseDir}/${configKubernetesDir}/${configFileName}.yml --set etcd-operator.customResources.createEtcdClusterCRD=false voltha
                        helm upgrade -f ../${configBaseDir}/${configKubernetesDir}/${configFileName}.yml --set etcd-operator.customResources.createEtcdClusterCRD=true voltha ./voltha
                        """
                    }
                    timeout(10) {
                        waitUntil {
                            voltha_completed = sh returnStdout: true, script: """
                            export KUBECONFIG=$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf &&
                            kubectl get pods -n voltha | grep -i running | grep 1/1 | wc -l
                            """
                            return voltha_completed.toInteger() == 8
                        }
                    }
                }
                stage('Install ONOS') {
                    timeout(10) {
                        sh returnStdout: true, script: """
                        export KUBECONFIG=$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf
                        helm install -n onos -f configs/onos.yaml -f ../${configBaseDir}/${configKubernetesDir}/${configFileName}.yml onos
                        """
                    }
                    timeout(10) {
                        waitUntil {
                            onos_completed = sh returnStdout: true, script: """
                            export KUBECONFIG=$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf &&
                            kubectl get pods | grep -i onos | grep -i running | grep 2/2 | wc -l
                            """
                            return onos_completed.toInteger() == 1
                        }
                    }
                }
                stage('Install xos-core') {
                    timeout(10) {
                        sh returnStdout: true, script: """
                        export KUBECONFIG=$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf
                        helm dep update xos-core
                        helm install -f ../${configBaseDir}/${configKubernetesDir}/${configFileName}.yml -n xos-core xos-core
                        """
                    }
                    timeout(10) {
                        waitUntil {
                            xos_core_completed = sh returnStdout: true, script: """
                            export KUBECONFIG=$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf &&
                            kubectl get pods | grep -i xos | grep -i running | grep 1/1 | wc -l
                            """
                            return xos_core_completed.toInteger() == 6
                        }
                    }
                }
                stage('Install att-workflow') {
                    timeout(10) {
                        sh returnStdout: true, script: """
                        export KUBECONFIG=$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf
                        helm dep update xos-profiles/att-workflow
                        helm install -f ../${configBaseDir}/${configKubernetesDir}/${configFileName}.yml -n att-workflow xos-profiles/att-workflow
                        """
                    }
                    timeout(10) {
                        waitUntil {
                            att_workflow_tosca_completed = sh returnStdout: true, script: """
                            export KUBECONFIG=$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf &&
                            kubectl get pods | grep -i att-workflow-tosca-loader | grep -i completed | wc -l
                            """
                            return att_workflow_tosca_completed.toInteger() == 1
                        }
                    }
                }
                stage('Install base-kubernetes') {
                    timeout(10) {
                        sh returnStdout: true, script: """
                        export KUBECONFIG=$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf
                        helm dep update xos-profiles/base-kubernetes
                        helm install -f ../${configBaseDir}/${configKubernetesDir}/${configFileName}.yml -n base-kubernetes xos-profiles/base-kubernetes
                        """
                    }
                    timeout(10) {
                        waitUntil {
                            base_kubernetes_tosca_running = sh returnStdout: true, script: """
                            export KUBECONFIG=$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf &&
                            kubectl get pods | grep -i base-kubernetes-tosca-loader | grep -i completed | wc -l
                            """
                            return base_kubernetes_tosca_running.toInteger() == 1
                        }
                    }
                }
            }
            stage('Restart OLT processes') {
                for(int i=0; i < deployment_config.olts.size(); i++) {
                    timeout(5) {
                        sh returnStdout: true, script: """
                        ssh-keyscan -H ${deployment_config.olts[i].ip} >> ~/.ssh/known_hosts
                        sshpass -p ${deployment_config.olts[i].pass} ssh -l ${deployment_config.olts[i].user} ${deployment_config.olts[i].ip} 'pkill bal_core_dist' || true
                        sshpass -p ${deployment_config.olts[i].pass} ssh -l ${deployment_config.olts[i].user} ${deployment_config.olts[i].ip} 'pkill openolt' || true
                        sshpass -p ${deployment_config.olts[i].pass} ssh -l ${deployment_config.olts[i].user} ${deployment_config.olts[i].ip} '> /broadcom/bal.log'
                        sshpass -p ${deployment_config.olts[i].pass} ssh -l ${deployment_config.olts[i].user} ${deployment_config.olts[i].ip} '> /broadcom/openolt.log'
                        sshpass -p ${deployment_config.olts[i].pass} ssh -l ${deployment_config.olts[i].user} ${deployment_config.olts[i].ip} 'cd /broadcom; ./bal_core_dist -C :55001 < /dev/tty1 > ./bal.log 2>&1 &'
                        sleep 5
                        sshpass -p ${deployment_config.olts[i].pass} ssh -l ${deployment_config.olts[i].user} ${deployment_config.olts[i].ip} 'cd /broadcom; ./openolt -C 127.0.0.1:55001 < /dev/tty1 > ./openolt.log 2>&1 &'
                        """
                    }
                    timeout(10) {
                        waitUntil {
                            onu_discovered = sh returnStdout: true, script: "sshpass -p ${deployment_config.olts[i].pass} ssh -l ${deployment_config.olts[i].user} ${deployment_config.olts[i].ip} 'cat /var/log/openolt.log | grep \"oper_state: up\" | wc -l'"
                            echo "ONU Discovered ${onu_discovered}"
                            return onu_discovered.toInteger() > 0
                        }
                    }
                }
            }
            if ( params.configurePod ) {
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
            }
            currentBuild.result = 'SUCCESS'
        } catch (err) {
            currentBuild.result = 'FAILURE'
            step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: "${notificationEmail}", sendToIndividuals: false])
        }
        echo "RESULT: ${currentBuild.result}"
    }
}