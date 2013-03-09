/*
 * Copyright 2009-2012 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.asterix.installer.command;

import java.util.Date;
import java.util.List;

import org.kohsuke.args4j.Option;

import edu.uci.ics.asterix.event.schema.pattern.Patterns;
import edu.uci.ics.asterix.installer.driver.InstallerDriver;
import edu.uci.ics.asterix.installer.driver.InstallerUtil;
import edu.uci.ics.asterix.installer.events.PatternCreator;
import edu.uci.ics.asterix.installer.model.AsterixInstance;
import edu.uci.ics.asterix.installer.model.AsterixInstance.State;
import edu.uci.ics.asterix.installer.model.BackupInfo;
import edu.uci.ics.asterix.installer.service.ServiceProvider;

public class BackupCommand extends AbstractCommand {

    @Override
    protected void execCommand() throws Exception {
        InstallerDriver.initConfig();
        String asterixInstanceName = ((BackupConfig) config).name;
        AsterixInstance instance = InstallerUtil.validateAsterixInstanceExists(asterixInstanceName, State.INACTIVE);
        List<BackupInfo> backupInfo = instance.getBackupInfo();
        PatternCreator pc = new PatternCreator();
        Patterns patterns = pc.getBackUpAsterixPattern(instance, ((BackupConfig) config).localPath);
        InstallerUtil.getEventrixClient(instance.getCluster()).submit(patterns);
        int backupId = backupInfo.size();
        BackupInfo binfo = new BackupInfo(backupId, new Date());
        backupInfo.add(binfo);
        LOGGER.info(asterixInstanceName + " backed up " + binfo);
        ServiceProvider.INSTANCE.getLookupService().updateAsterixInstance(instance);
    }

    @Override
    protected CommandConfig getCommandConfig() {
        return new BackupConfig();
    }

    @Override
    protected String getUsageDescription() {
        return "\nIn an undesirable event of data loss either due to a disk/system"
                + "\nfailure or accidental execution of a DDL statement (drop dataverse/dataset),"
                + "\nyou may need to recover the lost data. The backup command allows you to take a"
                + "\nbackup of the data stored with an ASTERIX instance. "
                + "\nThe backed up snapshot is stored in HDFS." 
                + "\n\nAvailable arguments/options:"
                + "\n-n name of the Asterix instance";

    }

}

class BackupConfig extends AbstractCommandConfig {

    @Option(name = "-n", required = false, usage = "Name of the Asterix instance")
    public String name;

    @Option(name = "-local", required = false, usage = "Path on the local file system for backup")
    public String localPath;

}
