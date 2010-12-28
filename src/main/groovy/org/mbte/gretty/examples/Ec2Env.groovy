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

@Typed class Ec2Env {
    PropertiesCredentials awsCredentials
    AmazonEC2Client awsClient

    String myIp = InetAddress.localHost.hostAddress

    Ec2Env () {
        File credentialsFile = [System.getProperty('user.home') + '/.aws/credentials']
        if(!credentialsFile.exists()) {
            throw new IOException(credentialsFile.absolutePath)
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

                    for(r in instances.reservations) {
                        if(r.groupNames[0] == myGroup) {
                            for(i in r.instances) {
                              if(myIp == i.privateIpAddress) {
                                  println "I am $i.privateIpAddress $i.tags"
                                  for(t in i.tags) {
                                      if(t.key.equalsIgnoreCase('role')) {
                                          myRole = t.value
                                      }
                                  }
                              }
                              else {
                                  for(t in i.tags) {
                                      if(t.key.equalsIgnoreCase('role') && t.value.equalsIgnoreCase('redis')) {
                                          myRedis = i.privateIpAddress
                                      }
                                  }
                              }
                            }
                        }
                    }

                    Thread.currentThread().sleep(15000)
                }
            }
        ]
        t.start()
    }
}
