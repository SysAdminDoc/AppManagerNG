// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.github.muntashirakon.AppManager.settings.Ops;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Denotes that the method, constructor or class does not contain any checks from {@link Ops}. This is useful to prevent
 * cycles when checking for root, ADB, etc.
 * Direct {@code Ops.*} references from annotated members should set {@link #used()} to {@code true}; the source-level
 * contract test pins that declaration.
 */
@Documented
@Retention(SOURCE)
@Target({METHOD, CONSTRUCTOR, TYPE})
public @interface NoOps {
    /**
     * Whether any {@link Ops} checks have been used.
     */
    boolean used() default false;
}
