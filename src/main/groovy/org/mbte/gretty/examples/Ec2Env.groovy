/*
 * Copyright 2009-2010 MBTE Sweden AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mbte.gretty.examples

import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.auth.PropertiesCredentials
import com.amazonaws.services.ec2.model.Instance

@Typed class Ec2Env {
    private PropertiesCredentials awsCredentials
    private AmazonEC2Client awsClient

    private String myIp = InetAddress.localHost.hostAddress

    private HashMap<String,Instance> myInstances = [:]

    Ec2Env () {
        File credentialsFile = [System.getProperty('user.home') + '/.aws/credentials']
        if(!credentialsFile.exists() || !credentialsFile.canRead()) {
            credentialsFile = ['../.aws/credentials']
            if(!credentialsFile.exists() || !credentialsFile.canRead()) {
                throw new IOException(credentialsFile.absolutePath)
            }
        }

        awsCredentials = [credentialsFile]
        awsClient = [awsCredentials]
    }

    void start () {
        Thread t = [
            run: {
                String myGroup
                for(;;) {
                    def instances = awsClient.describeInstances()

                    synchronized(this) {
                        if(!myGroup) {
                            for(r in instances.reservations) {
                                for(i in r.instances) {
                                  if(myIp == i.privateIpAddress) {
                                      myGroup = r.groupNames [0]
                                      break
                                  }
                                }
                            }
                        }

                        Map<String,Instance> insts = [:]

                        Set<Instance> newi = []
                        for(r in instances.reservations) {
                            if(!myGroup || r.groupNames[0] == myGroup) {
                                for(i in r.instances) {
                                  if(myIp == i.privateIpAddress) {
                                      println "I am $i.privateIpAddress $i.tags"
                                  }
                                  else {
                                      insts[i.instanceId] = i
                                      if(!myInstances.containsKey(i.instanceId)) {
                                          newi << i
                                          myInstances[i.instanceId] = i
                                          println "New instance $i.privateIpAddress $i.tags"
                                      }
                                  }
                                }
                            }
                        }

                        Set<String> gone = []
                        for(ii in myInstances.entrySet()) {
                            if(!insts.containsKey(ii.key)) {
                                gone << ii.key
                                println "Instance has gone $ii.value.privateIpAddress $ii.value.tags"
                            }
                        }
                        for(g in gone) {
                            myInstances.remove(g)
                        }
                    }
                    Thread.currentThread().sleep(15000)
                }
            }
        ]
        t.start()
    }

    Map<String,Instance> getInstances () {
        synchronized(this) {
            return myInstances.clone()
        }
    }
}
