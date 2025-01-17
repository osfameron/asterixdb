/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.asterix.cloud.clients.aws.s3;

import org.apache.asterix.common.config.CloudProperties;

import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

public class S3ClientConfig {

    private final String region;
    private final String endpoint;
    private final String prefix;
    private final boolean anonymousAuth;

    public S3ClientConfig(String region, String endpoint, String prefix, boolean anonymousAuth) {
        this.region = region;
        this.endpoint = endpoint;
        this.prefix = prefix;
        this.anonymousAuth = anonymousAuth;
    }

    public static S3ClientConfig of(CloudProperties cloudProperties) {
        return new S3ClientConfig(cloudProperties.getStorageRegion(), cloudProperties.getStorageEndpoint(),
                cloudProperties.getStoragePrefix(), cloudProperties.isStorageAnonymousAuth());
    }

    public String getRegion() {
        return region;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getPrefix() {
        return prefix;
    }

    public AwsCredentialsProvider createCredentialsProvider() {
        return anonymousAuth ? AnonymousCredentialsProvider.create() : DefaultCredentialsProvider.create();
    }
}
