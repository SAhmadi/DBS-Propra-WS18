package de.hhu.cs.dbs.propra.configurations;

import de.hhu.cs.dbs.propra.repositories.SQLiteUserRepository;
import de.hhu.cs.dbs.propra.repositories.UserRepository;
import de.hhu.cs.dbs.propra.services.BasicHTTPAuthenticationService;
import de.hhu.cs.dbs.propra.services.CustomAuthorizationService;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.File;
import java.io.PrintWriter;
import java.sql.SQLException;

public class AbstractBinder extends org.glassfish.hk2.utilities.binding.AbstractBinder {
    @Override
    protected void configure() {
        bind(getDataSource()).to(DataSource.class);
        bind(SQLiteUserRepository.class).to(UserRepository.class);
        bindAsContract(AuthorizationContext.class);
        bindAsContract(BasicHTTPAuthenticationService.class);
        bindAsContract(CustomAuthorizationService.class);
    }

    private SQLiteDataSource getDataSource() {
        SQLiteConfig config = new SQLiteConfig();
        config.setEncoding(SQLiteConfig.Encoding.UTF8);
        config.enforceForeignKeys(true);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        try {
            dataSource.setLogWriter(new PrintWriter(System.out));
        } catch (SQLException exception) {
            System.err.println(exception.getMessage());
        }
        dataSource.setUrl("jdbc:sqlite:data" + File.separator + "database.db");
        try {
            dataSource.getConnection().createStatement().execute("PRAGMA auto_vacuum = 1;");
        } catch (SQLException exception) {
            System.err.println(exception.getMessage());
        }
        return dataSource;
    }
}
