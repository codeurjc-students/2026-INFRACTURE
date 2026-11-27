package es.codeurjc.infracture.demo;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// The experiment's PostgreSQL, only for DATABASE_CRUD. The plan gives its address and user in DATABASE_URL
// and DATABASE_USER, and its temporary password in DATABASE_PASSWORD. Health includes the connection.
@Configuration
@ConditionalOnProperty(name = "APP_MODE", havingValue = "DATABASE_CRUD")
public class DatabaseConfiguration {

    // A short wait for a connection, so /health answers DOWN before its check times out.
    private static final long CONNECTION_TIMEOUT_MS = 1500;
    private static final int MAX_CONNECTIONS = 5;

    // DATABASE_URL reads postgresql://host:port/database, which JDBC writes with a "jdbc:" prefix.
    @Bean
    public DataSource dataSource(@Value("${DATABASE_URL}") String url, @Value("${DATABASE_USER}") String user,
            @Value("${DATABASE_PASSWORD}") String password) {
        if (!url.startsWith("postgresql://")) {
            throw new IllegalStateException("DATABASE_URL must start with postgresql://");
        }
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:" + url);
        dataSource.setUsername(user);
        dataSource.setPassword(password);
        dataSource.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        dataSource.setMaximumPoolSize(MAX_CONNECTIONS);
        return dataSource;
    }
}
