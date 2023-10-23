package com.github.tenants.plugin.ex;

/**
 * 表示特定于租户框架的异常。
 * <p>
 * 当框架内租户代码错误时，通常会引发此异常。
 * 它是 RuntimeException 的一个子类，继承了它的所有功能。
 *
 * @author xierh
 * @since 2023/10/13 10:57
 */
public class TenantException extends RuntimeException {

    public TenantException(String message) {
        super(message);
    }

    public TenantException(String message, Throwable cause) {
        super(message, cause);
    }
}
