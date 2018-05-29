/*
 * Copyright (C) 2018 Nils Petzaell
 *
 * This file is part of SchemaSpy.
 *
 * SchemaSpy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SchemaSpy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with SchemaSpy. If not, see <http://www.gnu.org/licenses/>.
 */
package org.schemaspy.integrationtesting.mysql;

import com.github.npetzall.testcontainers.junit.jdbc.JdbcContainerRule;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.schemaspy.Config;
import org.schemaspy.cli.CommandLineArgumentParser;
import org.schemaspy.cli.CommandLineArguments;
import org.schemaspy.integrationtesting.MysqlSuite;
import org.schemaspy.model.*;
import org.schemaspy.service.DatabaseService;
import org.schemaspy.service.SqlService;
import org.schemaspy.testing.SuiteOrTestJdbcContainerRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.MySQLContainer;

import javax.script.ScriptException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static com.github.npetzall.testcontainers.junit.jdbc.JdbcAssumptions.assumeDriverIsPresent;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Nils Petzaell
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@DirtiesContext
public class MysqlSpacesNoDotsIT {

    @Autowired
    private SqlService sqlService;

    @Autowired
    private DatabaseService databaseService;

    @Mock
    private ProgressListener progressListener;

    @Autowired
    private CommandLineArgumentParser commandLineArgumentParser;

    private static Database database;

    @ClassRule
    public static JdbcContainerRule<MySQLContainer> jdbcContainerRule =
            new SuiteOrTestJdbcContainerRule<>(
                    MysqlSuite.jdbcContainerRule,
                    new JdbcContainerRule<>(() -> new MySQLContainer("mysql:5"))
                            .assumeDockerIsPresent()
                            .withAssumptions(assumeDriverIsPresent())
                            .withInitScript("integrationTesting/mysql/dbScripts/spacesnodotsit.sql")
                            .withInitUser("root", "test")
            );

    @Before
    public synchronized void createDatabaseRepresentation() throws SQLException, IOException, ScriptException, URISyntaxException {
        if (database == null) {
            doCreateDatabaseRepresentation();
        }
    }

    private void doCreateDatabaseRepresentation() throws SQLException, IOException, URISyntaxException {
        String[] args = {
                "-t", "mysql",
                "-db", "TEST 1",
                "-s", "TEST 1",
                "-cat", "%",
                "-o", "target/integrationtesting/mysql_spaces_no_dots",
                "-u", "test",
                "-p", "test",
                "-host", jdbcContainerRule.getContainer().getContainerIpAddress(),
                "-port", jdbcContainerRule.getContainer().getMappedPort(3306).toString()
        };
        CommandLineArguments arguments = commandLineArgumentParser.parse(args);
        Config config = new Config(args);
        DatabaseMetaData databaseMetaData = sqlService.connect(config);
        Database database = new Database(
                databaseMetaData,
                arguments.getDatabaseName(),
                arguments.getCatalog(),
                arguments.getSchema(),
                null
        );
        databaseService.gatheringSchemaDetails(config, database, progressListener);
        this.database = database;
    }

    @Test
    public void databaseShouldExist() {
        assertThat(database).isNotNull();
        assertThat(database.getName()).isEqualToIgnoringCase("TEST 1");
    }

    @Test
    public void databaseShouldHaveTable() {
        assertThat(database.getTables()).extracting(Table::getName).contains("TABLE 1");
    }

    @Test
    public void tableShouldHavePKWithAutoIncrement() {
        assertThat(database.getTablesByName().get("TABLE 1").getColumns()).extracting(TableColumn::getName).contains("id");
        assertThat(database.getTablesByName().get("TABLE 1").getColumn("id").isPrimary()).isTrue();
        assertThat(database.getTablesByName().get("TABLE 1").getColumn("id").isAutoUpdated()).isTrue();
    }

    @Test
    public void tableShouldHaveForeignKey() {
        assertThat(database.getTablesByName().get("TABLE 1").getForeignKeys()).extracting(ForeignKeyConstraint::getName).contains("link fk");
    }

    @Test
    public void tableShouldHaveUniqueKey() {
        assertThat(database.getTablesByName().get("TABLE 1").getIndexes()).extracting(TableIndex::getName).contains("name_link_unique");
    }

    @Test
    public void tableShouldHaveColumnWithSpaceInIt() {
        assertThat(database.getTablesByName().get("TABLE 1").getColumns()).extracting(TableColumn::getName).contains("link id");
    }
}