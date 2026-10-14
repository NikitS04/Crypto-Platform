package com.nikit.support;

import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.dialect.PostgreSQLSqlAstTranslator;
import org.hibernate.dialect.RowLockStrategy;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;
import org.hibernate.sql.ast.spi.StandardSqlAstTranslatorFactory;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.exec.spi.JdbcOperation;

/**
 * Test-only dialect: generates the same schema as production PostgreSQL, but renders row locks as
 * plain "FOR UPDATE". PostgreSQL's "FOR NO KEY UPDATE" / "FOR SHARE" aren't understood by H2.
 */
public class H2PostgreSQLDialect extends PostgreSQLDialect {

    @Override
    public SqlAstTranslatorFactory getSqlAstTranslatorFactory() {
        return new StandardSqlAstTranslatorFactory() {
            @Override
            protected <T extends JdbcOperation> SqlAstTranslator<T> buildTranslator(
                    SessionFactoryImplementor sessionFactory, Statement statement) {
                return new PostgreSQLSqlAstTranslator<>(sessionFactory, statement) {
                    @Override
                    protected String getForUpdate() {
                        return " for update";
                    }

                    @Override
                    protected String getForShare(int timeoutMillis) {
                        return " for update";
                    }
                };
            }
        };
    }

    @Override
    public RowLockStrategy getWriteRowLockStrategy() {
        return RowLockStrategy.NONE;
    }
}
