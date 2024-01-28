package io.github.gaming32.nestsgenerator;

class UncheckedClassNotFoundException extends RuntimeException {
    public UncheckedClassNotFoundException(ClassNotFoundException e) {
        super(e);
    }

    @Override
    public synchronized ClassNotFoundException getCause() {
        return (ClassNotFoundException)super.getCause();
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
