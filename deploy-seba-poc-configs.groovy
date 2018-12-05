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
            dir ("helm-charts") {
                stage('Push pppoe-oar to ONOS') {
                    timeout(5) {
                        sh returnStdout: true, script: """
                        export KUBECONFIG=$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf
                        export onosDockerName=`kubectl get pods |grep onos|grep -v att|cut -d " " -f1`
                        echo \$onosDockerName
                        kubectl exec `kubectl get pods |grep onos|grep -v att|cut -d " " -f1` -- bash -c "rm /home/sdn/oars/tt-pppoe-1.0.0-SNAPSHOT.oar"
                        kubectl exec `kubectl get pods |grep onos|grep -v att|cut -d " " -f1` -- bash -c "ls /home/sdn/oars/" 
                        kubectl cp ../pod-configs/tosca-configs/att-workflow/tt-pppoe-1.0.0-SNAPSHOT.oar \$onosDockerName:/home/sdn/oars/.
                        sleep 10                     
                        """
                        return true
                    }
                }
                stage('Change ONOS OAR ') {
                    timeout(10) {
                        sh returnStdout: true, script: """
                        export KUBECONFIG=$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf
                        kubectl exec `kubectl get pods |grep onos|grep -v att|cut -d " " -f1` -- bash -c "bin/onos-app localhost uninstall org.opencord.kafka"
                        sleep 10
                        kubectl exec `kubectl get pods |grep onos|grep -v att|cut -d " " -f1` -- bash -c "bin/onos-app localhost uninstall org.opencord.dhcpl2relay"
                        sleep 10
                        kubectl exec `kubectl get pods |grep onos|grep -v att|cut -d " " -f1` -- bash -c "bin/onos-app localhost uninstall org.opencord.aaa"
                        sleep 10
                        kubectl exec `kubectl get pods |grep onos|grep -v att|cut -d " " -f1` -- bash -c "bin/onos-app localhost uninstall org.opencord.olt"
                        sleep 10
                        kubectl exec `kubectl get pods |grep onos|grep -v att|cut -d " " -f1` -- bash -c "bin/onos-app localhost uninstall org.opencord.sadis"
                        sleep 10
                        kubectl exec `kubectl get pods |grep onos|grep -v att|cut -d " " -f1` -- bash -c "bin/onos-app localhost uninstall org.opencord.config"
                        sleep 10
                        
                        
                        
                        kubectl exec `kubectl get pods |grep onos|grep -v att|cut -d " " -f1` -- bash -c "bin/onos-app localhost install /home/sdn/oars/cord-config-1.5.0-SNAPSHOT.oar"
                        sleep 10
                        kubectl exec `kubectl get pods |grep onos|grep -v att|cut -d " " -f1` -- bash -c "bin/onos-app localhost activate org.opencord.config"
                        sleep 10
                        
                        kubectl exec `kubectl get pods |grep onos|grep -v att|cut -d " " -f1` -- bash -c "bin/onos-app localhost install /home/sdn/oars/sadis-app-3.0.0-SNAPSHOT.oar"
                        sleep 10
                        kubectl exec `kubectl get pods |grep onos|grep -v att|cut -d " " -f1` -- bash -c "bin/onos-app localhost activate org.opencord.sadis"
                        sleep 10
                        
                        kubectl exec `kubectl get pods |grep onos|grep -v att|cut -d " " -f1` -- bash -c "bin/onos-app localhost install /home/sdn/oars/olt-app-2.1.0-SNAPSHOT.oar"
                        sleep 10
                        kubectl exec `kubectl get pods |grep onos|grep -v att|cut -d " " -f1` -- bash -c "bin/onos-app localhost activate org.opencord.olt"
                        sleep 10
                        
                        kubectl exec `kubectl get pods |grep onos|grep -v att|cut -d " " -f1` -- bash -c "bin/onos-app localhost install /home/sdn/oars/aaa-1.8.0-SNAPSHOT.oar"
                        sleep 10
                        kubectl exec `kubectl get pods |grep onos|grep -v att|cut -d " " -f1` -- bash -c "bin/onos-app localhost activate org.opencord.aaa"
                        sleep 10
                        
                        kubectl exec `kubectl get pods |grep onos|grep -v att|cut -d " " -f1` -- bash -c "bin/onos-app localhost install /home/sdn/oars/tt-pppoe-1.0.0-SNAPSHOT.oar"
                        sleep 10
                        kubectl exec `kubectl get pods |grep onos|grep -v att|cut -d " " -f1` -- bash -c "bin/onos-app localhost activate org.opencord.tt-pppoe"
                        sleep 10
                        
                        kubectl exec `kubectl get pods |grep onos|grep -v att|cut -d " " -f1` -- bash -c "bin/onos-app localhost install /home/sdn/oars/dhcpl2relay-1.5.0-SNAPSHOT.oar"
                        sleep 10
                        kubectl exec `kubectl get pods |grep onos|grep -v att|cut -d " " -f1` -- bash -c "bin/onos-app localhost activate org.opencord.dhcpl2relay"
                        sleep 10
                        
                        kubectl exec `kubectl get pods |grep onos|grep -v att|cut -d " " -f1` -- bash -c "bin/onos-app localhost install /home/sdn/oars/kafka-1.0.0-SNAPSHOT.oar"
                        sleep 10
                        kubectl exec `kubectl get pods |grep onos|grep -v att|cut -d " " -f1` -- bash -c "bin/onos-app localhost activate org.opencord.kafka"
						sleep 10
						"""
                        return true
                    }
                }
                stage('Push netcfg to ONOS') {
                    timeout(5) {
                        sh returnStdout: true, script: """
                        export KUBECONFIG=$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf
                        export sadisIp=`kubectl get pods -o wide |grep sadis|awk '{print \$6}'`
                        export onosDockerName=`kubectl get pods |grep onos|grep -v att|cut -d " " -f1`
                        echo \$sadisIp
                        echo \$onosDockerName
                        sed -i.bak '/subscriber/c\\          "url":"http://'\$sadisIp':8000/subscriber/%s",' ../pod-configs/tosca-configs/att-workflow/config.json
                        kubectl cp ../pod-configs/tosca-configs/att-workflow/config.json \$onosDockerName:/root/onos/bin/.
                        kubectl exec `kubectl get pods |grep onos|grep -v att|cut -d " " -f1` -- bash -c "sed -i.bak '/check/d' /root/onos/bin/onos-netcfg"  
                        kubectl exec `kubectl get pods |grep onos|grep -v att|cut -d " " -f1` -- bash -c "./bin/onos-netcfg localhost bin/config.json"                     
                        """
                        return true
                    }
                }
                stage('Set vlan35') {
                    timeout(5) {
                        sh returnStdout: true, script: """
                        export KUBECONFIG=$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf
                        ssh-keyscan -p 30115 -H 192.168.70.21 >> ~/.ssh/known_hosts
                        sshpass -p rocks ssh -p 30115  onos@192.168.70.21 'cfg set org.opencord.olt.impl.Olt defaultVlan ${defaultVlan}' || true
                        """
                        return true
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
            stage('vOLT Add Subscriber Flows') {
                timeout(5) {
                    sh returnStdout: true, script: """
                        export KUBECONFIG=$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf
                        ssh-keyscan -p 30115 -H 192.168.70.21 >> ~/.ssh/known_hosts
                        sleep 1m
                        sshpass -p rocks ssh -p 30115  onos@192.168.70.21 'volt-add-subscriber-access ${oltDpID} ${ontUniPort}'
                        """
                    return true
                }
            }
            stage('Configure Host PPPOE') {
                timeout(5) {
                    sh returnStdout: true, script: """
                        export KUBECONFIG=$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf
                        ssh-keyscan -p 30115 -H 192.168.70.21 >> ~/.ssh/known_hosts
                        sshpass -p rocks ssh -p 30115  onos@192.168.70.21 'cfg set org.onosproject.pppoe.PppoeManager bngConnectPort ${bngConnectPort}'
                        sshpass -p rocks ssh -p 30115  onos@192.168.70.21 'cfg set org.onosproject.pppoe.PppoeManager deviceId ${deviceId}'
                        sshpass -p rocks ssh -p 30115  onos@192.168.70.21 'cfg set org.onosproject.pppoe.PppoeManager pppoeEndpointMacAddress ${pppoeEndpointMacAddress}'
                        sshpass -p rocks ssh -p 30115  onos@192.168.70.21 'cfg set org.onosproject.pppoe.PppoeManager bngMacAddress ${bngMacAddress}'
                        sshpass -p rocks ssh -p 30115  onos@192.168.70.21 'cfg set org.onosproject.pppoe.PppoeManager stag ${stag}'
                        sleep 2
                        sshpass -p rocks ssh -p 30115  onos@192.168.70.21 'pppoe'
                        """
                    return true
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