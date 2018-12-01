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
            dir ("helm-charts"){
                stage('Change ONOS OAR Repo') {
                    timeout(10) {
                        sh returnStdout: true, script: """
							export KUBECONFIG=$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf
							sed -i.bak '/aaaAppUrl/c\\aaaAppUrl: "http://192.168.45.29:8080/repository/seba/onos/aaa/1.8.0-SNAPSHOT/aaa-1.8.0-20181129.055735-1.oar"' xos-profiles/att-workflow/values.yaml
                            sed -i.bak '/oltAppUrl/c\\oltAppUrl: "http://192.168.45.29:8080/repository/seba/onos/olt-app/2.1.0-SNAPSHOT/olt-app-2.1.0-20181129.055807-1.oar"' xos-profiles/att-workflow/values.yaml
                            sed -i.bak '/sadisAppUrl/c\\sadisAppUrl: "http://192.168.45.29:8080/repository/seba/onos/sadis-app/3.0.0-SNAPSHOT/sadis-app-3.0.0-20181129.055818-1.oar"' xos-profiles/att-workflow/values.yaml
                            sed -i.bak '/dhcpl2relayAppUrl/c\\dhcpl2relayAppUrl: "http://192.168.45.29:8080/repository/seba/onos/dhcpl2relay/1.5.0-SNAPSHOT/dhcpl2relay-1.5.0-20181129.055745-1.oar"' xos-profiles/att-workflow/values.yaml
                            sed -i.bak '/kafkaAppUrl/c\\kafkaAppUrl: "http://192.168.45.29:8080/repository/seba/onos/kafka/1.0.0-SNAPSHOT/kafka-1.0.0-20181129.055757-1.oar"' xos-profiles/att-workflow/values.yaml
                            sed -i.bak '/kafkaAppUrl/a\\pppoeAppUrl: "http://192.168.45.29:8080/repository/seba/onos/tt-pppoe/1.0.0-SNAPSHOT/tt-pppoe-1.0.0-20181129.055826-1.oar"' xos-profiles/att-workflow/values.yaml
                            sed -i '122 i\\    onos_app#pppoe:\\n      type: tosca.nodes.ONOSApp\\n      properties:\\n        name: pppoe\\n        app_id: org.opencord.pppoe\\n        url: {{ .pppoeAppUrl }}\\n        version: 1.0.0.SNAPSHOT\\n      requirements:\\n        - owner:\\n            node: service#onos\\n            relationship: tosca.relationships.BelongsToOne\\n' xos-profiles/att-workflow/templates/_tosca.tpl  
							"""
                        return true
                    }
                }
                stage('Push netcfg to ONOS') {
                    timeout(10) {
                        sh returnStdout: true, script: """
                        export KUBECONFIG=$WORKSPACE/${configBaseDir}/${configKubernetesDir}/${configFileName}.conf
                        export sadisIp=`kubectl get pods -o wide |grep sadis|awk '{print \$6}'`
                        export onosDockerName=`kubectl get pods |grep onos|grep -v att|cut -d " " -f1`
                        echo \$sadisIp
                        echo \$onosDockerName
                        sed -i.bak '/subscriber/c\\          "url":"http://'\$sadisIp':8000/subscriber/%s",' ../pod-configs/tosca-configs/att-workflow/config.json
                        kubectl cp ../pod-configs/tosca-configs/att-workflow/config.json \$onosDockerName:/root/onos/bin/.
                        kubectl exec `kubectl get pods |grep onos|grep -v att|cut -d " " -f1` -- bash -c "sed -i.bak '/check/d' /root/onos/bin/onos-netcfg"  
                        kubectl exec onos-84c7b4d6d5-cv7lc -- bash -c "./bin/onos-netcfg localhost bin/config.json"                     
                        """
                        return true
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