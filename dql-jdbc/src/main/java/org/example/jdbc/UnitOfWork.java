package org.example.jdbc;


@FunctionalInterface
public interface UnitOfWork extends AutoCloseable {

    @Override
    void close();
}
