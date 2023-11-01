/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
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
package org.atalk.persistance.migrations

import android.database.sqlite.SQLiteDatabase

object Migrations {
    fun upgradeDatabase(db: SQLiteDatabase, migrationsHelper: MigrationsHelper?) {
        when (db.version) {
            1 -> {
                MigrationTo2.createOmemoTables(db)
                MigrationTo3.updateSQLDatabase(db)
                MigrationTo4.updateOmemoIdentitiesTable(db)
                MigrationTo5.updateOmemoDevicesTable(db)
                MigrationTo6.updateChatSessionTable(db)
            }
            2 -> {
                MigrationTo3.updateSQLDatabase(db)
                MigrationTo4.updateOmemoIdentitiesTable(db)
                MigrationTo5.updateOmemoDevicesTable(db)
                MigrationTo6.updateChatSessionTable(db)
            }
            3 -> {
                MigrationTo4.updateOmemoIdentitiesTable(db)
                MigrationTo5.updateOmemoDevicesTable(db)
                MigrationTo6.updateChatSessionTable(db)
            }
            4 -> {
                MigrationTo5.updateOmemoDevicesTable(db)
                MigrationTo6.updateChatSessionTable(db)
            }
            5 -> MigrationTo6.updateChatSessionTable(db)
        }
    }
}