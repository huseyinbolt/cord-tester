node ("${podName}") {
    timeout (100) {
        try {
            stage ("Parse deployment configuration file") {
                sh returnStdout: true, script: "rm -rf ${configBaseDir}"
                sh returnStdout: true, script: "git clone https://github.com/mustafaeurfali/pod-configs.git"
                deployment_config = readYaml file: "${configBaseDir}/${configDeploymentDir}/${configFileName}.yaml"
            }
            stage('Clean up') {
                timeout(10) {
                    sh returnStdout: true, script: """
                    rm -rf helm-charts cord-tester
                    git clone -b ${branch} ${cordRepoUrl}/helm-charts
                    export KUBECONFIG=/var/lib/jenkins/workspace/admin.conf &&
                    for hchart in \$(helm list -q | grep -E -v 'docker-registry|mavenrepo|ponnet');
                    do
                        echo "Purging chart: \${hchart}"
                        helm delete --purge "\${hchart}"
                    done
                    """
                    timeout(5) {
                        waitUntil {
                            helm_deleted = sh returnStdout: true, script: """
                            export KUBECONFIG=/var/lib/jenkins/workspace/admin.conf &&
                            helm ls -q | grep -E -v 'docker-registry|mavenrepo|ponnet' | wc -l
                            """
                            return helm_deleted.toInteger() == 0
                        }
                    }
                    timeout(5) {
                        waitUntil {
                            kubectl_deleted = sh returnStdout: true, script: """
                            export KUBECONFIG=/var/lib/jenkins/workspace/admin.conf &&
                            kubectl get pods --all-namespaces --no-headers | grep -E -v 'kube-system|docker-registry|mavenrepo|ponnet' | wc -l
                            """
                            return kubectl_deleted.toInteger() == 0
                        }
                    }
                }
            }
            dir ("helm-charts") {
                stage('Install CORD Kafka') {
                    timeout(15) {
                        sh returnStdout: true, script: """
                        export KUBECONFIG=/var/lib/jenkins/workspace/admin.conf &&
                        helm install -f examples/kafka-single.yaml -f ../${configBaseDir}/${configKubernetesDir}/${configFileName}.yml --version 0.8.8 -n cord-kafka incubator/kafka
                        """
                    }
                    timeout(15) {
                        waitUntil {
                            kafka_instances_running = sh returnStdout: true, script: """
                            export KUBECONFIG=/var/lib/jenkins/workspace/admin.conf &&
                            kubectl get pods | grep cord-kafka | grep -i running | grep 1/1 | wc -l
                            """
                            return kafka_instances_running.toInteger() == 2
                        }
                    }
                }
                stage('Install etcd-cluster') {
                    timeout(15) {
                        sh returnStdout: true, script: """
                        export KUBECONFIG=/var/lib/jenkins/workspace/admin.conf
                        helm install -f ../${configBaseDir}/${configKubernetesDir}/${configFileName}.yml --version 0.8.0 -n etcd-operator stable/etcd-operator
                        """
                    }
                    timeout(15) {
                        waitUntil {
                            etcd_running = sh returnStdout: true, script: """
                            export KUBECONFIG=/var/lib/jenkins/workspace/admin.conf &&
                            kubectl get pods | grep etcd | grep -i running | grep 1/1 | wc -l
                            """
                            return etcd_running.toInteger() == 3
                        }
                    }
                }
                stage('Install voltha') {
                    timeout(15) {
                        sh returnStdout: true, script: """
                        export KUBECONFIG=/var/lib/jenkins/workspace/admin.conf
                        helm repo add incubator https://kubernetes-charts-incubator.storage.googleapis.com/
                        helm dep build voltha
                        helm install -n voltha -f ../${configBaseDir}/${configKubernetesDir}/${configFileName}.yml voltha
                        """
                    }
                    timeout(15) {
                        waitUntil {
                            voltha_completed = sh returnStdout: true, script: """
                            export KUBECONFIG=/var/lib/jenkins/workspace/admin.conf &&
                            kubectl get pods -n voltha | grep -i running | grep 1/1 | wc -l
                            """
                            return voltha_completed.toInteger() == 8
                        }
                    }
                }
                stage('Install ONOS') {
                    timeout(10) {
                        sh returnStdout: true, script: """
                        export KUBECONFIG=/var/lib/jenkins/workspace/admin.conf
                        helm install -n onos -f ../${configBaseDir}/${configKubernetesDir}/${configFileName}.yml onos
                        """
                    }
                    timeout(10) {
                        waitUntil {
                            onos_completed = sh returnStdout: true, script: """
                            export KUBECONFIG=/var/lib/jenkins/workspace/admin.conf &&
                            kubectl get pods | grep -i onos | grep -i running | grep 2/2 | wc -l
                            """
                            return onos_completed.toInteger() == 1
                        }
                    }
                }
                stage('Install xos-core') {
                    timeout(15) {
                        sh returnStdout: true, script: """
                        export KUBECONFIG=/var/lib/jenkins/workspace/admin.conf
                        helm dep update xos-core
                        helm install -f ../${configBaseDir}/${configKubernetesDir}/${configFileName}.yml -n xos-core xos-core
                        """
                    }
                    timeout(15) {
                        waitUntil {
                            xos_core_completed = sh returnStdout: true, script: """
                            export KUBECONFIG=/var/lib/jenkins/workspace/admin.conf
                            kubectl get pods | grep -i xos | grep -i running | grep 1/1 | wc -l
                            """
                            return xos_core_completed.toInteger() == 6
                        }
                    }
                }
                stage('Install att-workflow') {
                    timeout(15) {
                        sh returnStdout: true, script: """
                        export KUBECONFIG=/var/lib/jenkins/workspace/admin.conf
                        helm dep update xos-profiles/att-workflow
                        helm install -f ../${configBaseDir}/${configKubernetesDir}/${configFileName}.yml -n att-workflow xos-profiles/att-workflow
                        """
                    }
                    timeout(15) {
                        waitUntil {
                            att_workflow_tosca_completed = sh returnStdout: true, script: """
                            export KUBECONFIG=/var/lib/jenkins/workspace/admin.conf
                            kubectl get pods | grep -i att-workflow-tosca-loader | grep -i completed | wc -l
                            """
                            return att_workflow_tosca_completed.toInteger() == 1
                        }
                    }
                }
                stage('Install base-kubernetes') {
                    timeout(15) {
                        sh returnStdout: true, script: """
                        export KUBECONFIG=/var/lib/jenkins/workspace/admin.conf
                        helm dep update xos-profiles/base-kubernetes
                        helm install -f ../${configBaseDir}/${configKubernetesDir}/${configFileName}.yml -n base-kubernetes xos-profiles/base-kubernetes
                        """
                    }
                    timeout(15) {
                        waitUntil {
                            base_kubernetes_tosca_running = sh returnStdout: true, script: """
                            export KUBECONFIG=/var/lib/jenkins/workspace/admin.conf &&
                            kubectl get pods | grep -i base-kubernetes-tosca-loader | grep -i completed | wc -l
                            """
                            return base_kubernetes_tosca_running.toInteger() == 1
                        }
                    }
                }
            }
            if ( params.configurePod ) {
                dir ("${configBaseDir}/${configToscaDir}/att-workflow") {
                    return true
                }
            }
            currentBuild.result = 'SUCCESS'
        } catch (err) {
            currentBuild.result = 'FAILURE'
        }
        echo "RESULT: ${currentBuild.result}"
    }
}