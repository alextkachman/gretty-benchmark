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

import com.amazonaws.auth.PropertiesCredentials
import com.amazonaws.services.ec2.AmazonEC2AsyncClient

@Typed class Ec2Env {
    static PropertiesCredentials awsCredentials
    static AmazonEC2AsyncClient  awsClient

    static String role

    static void main (String [] args) {
        File credentialsFile = [System.getProperty('user.home') + '/.aws/credentials']
        if(!credentialsFile.exists()) {
            throw new IOException(credentialsFile.absolutePath)
        }

        awsCredentials = [credentialsFile]
        awsClient = [awsCredentials]

        Thread t = [
            run: {
                def ip = InetAddress.localHost.hostAddress

                for(;;) {
                    def instances = awsClient.describeInstances()
                    def myRole = ''
                    for(r in instances.reservations) {
                        for(i in r.instances) {
                          if(ip == i.privateIpAddress) {
                              println "I am $i.privateIpAddress $i.tags"
                              for(t in i.tags) {
                                  if(t.key.equalsIgnoreCase('role')) {
                                      myRole = t.value
                                  }
                              }
                          }
                        }
                    }

                    if(myRole == '') {
                        myRole == 'server'
                    }

                    if(myRole != role) {
                        stopRole(role)
                        role = myRole
                        startRole(role)
                    }

                    Thread.sleep 15000
                }
            }
        ]
        t.start()
    }

    static void startRole (String role) {
        println "Starting role '$role'"
    }

    static void stopRole (String role) {
        println "Stopping role '$role'"
    }
}
