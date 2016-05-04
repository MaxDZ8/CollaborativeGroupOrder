package com.massimodz8.collaborativegrouporder;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.CLASS;


/**
 * Created by Massimo on 04/05/2016.
 * A problem with order shuffling is that I could end up confused about what's an int-position-index
 * in the array and what's an int-actor-id or peerKey.
 * Solve this with an annotation!
 */
@Retention(CLASS)
@Target({PARAMETER,METHOD,LOCAL_VARIABLE,FIELD})
public @interface ActorId {
}