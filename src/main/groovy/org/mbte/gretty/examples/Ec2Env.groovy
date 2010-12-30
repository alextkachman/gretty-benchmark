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
import groovypp.channels.LoopChannel
import com.amazonaws.services.ec2.model.Reservation
import java.util.concurrent.Executors

@Typed class Ec2Env extends LoopChannel {
    private PropertiesCredentials awsCredentials
    private AmazonEC2Client awsClient

    private String myIp = InetAddress.localHost.hostAddress

    private HashMap<String,Instance> myInstances = [:]

    private Reservation myReservation
    private Instance myInstance

    static class ForeignInstanceUpdate {
        Instance current, old
    }

    static class OwnInstanceUpdate {
        Instance current, old
    }

    void doStartup() {
        println('Starting EC2 monitor...')

        File credentialsFile = [System.getProperty('user.home') + '/.aws/credentials']
        if(!credentialsFile.exists() || !credentialsFile.canRead()) {
            credentialsFile = ['../.aws/credentials']
            if(!credentialsFile.exists() || !credentialsFile.canRead()) {
                throw new IOException(credentialsFile.absolutePath)
            }
        }

        awsCredentials = [credentialsFile]
        awsClient = [awsCredentials]

        def instances = awsClient.describeInstances()

        for (r in instances.reservations) {
            for (i in r.instances) {
                if (myIp == i.privateIpAddress) {
                    myInstance = i
                    myReservation = r
                    break
                }
            }
        }

        owner << new OwnInstanceUpdate(current: myInstance, old: null)

        super.doStartup()
    }

    void doShutdown () {
        println('Stopping EC2 monitor...')
        super.doShutdown()
    }

    protected boolean doLoopAction() {
        def instances = awsClient.describeInstances()

        Map<String,Instance> insts = [:]

        for(r in instances.reservations) {
            if(!myReservation || r.groupNames[0] == myReservation.groupNames[0]) {
                for(i in r.instances) {
                  if(myIp == i.privateIpAddress) {
                      def old = myInstance
                      myInstance = i
                      owner << new OwnInstanceUpdate(current: i, old: old)
                  }
                  else {
                      insts[i.instanceId] = i
                      def old = myInstances[i.instanceId]
                      myInstances[i.instanceId] = i
                      if(!old) {
                          owner << new ForeignInstanceUpdate(current: i, old: null)
                      }
                      else {
                          owner << new ForeignInstanceUpdate(current: i, old: old)
                      }
                  }
                }
            }
        }

        Set<String> gone = []
        for(ii in myInstances.entrySet()) {
            if(!insts.containsKey(ii.key)) {
                gone << ii.key
            }
        }
        for(g in gone) {
            def old = myInstances.remove(g)
            owner << new ForeignInstanceUpdate(current: null, old: old)
        }

        Thread.currentThread().sleep(15000)
        return  true
    }
}
