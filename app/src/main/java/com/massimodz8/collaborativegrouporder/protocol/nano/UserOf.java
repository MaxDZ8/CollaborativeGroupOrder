package com.massimodz8.collaborativegrouporder.protocol.nano;

/**
 * Created by Massimo on 17/06/2016.
 * This annotates variables used to reference the persistent 'activity state'.
 * There are two types of activities.
 * 1- Those creating stuff and putting them into RunningServiceHandles... those are the 'owners'
 *    and mangle lifetime by setting =null to the references.
 * 2- Those taking for granted stuff is here.
 *
 * In Java we cannot destroy stuff as accurately as I want but accessing nullptr is just as
 * dangerous so setting a unique reference to null is basically the same.
 * For this reason, activities using persistent state must keep an internal reference to state
 * so even if the owner sets RSH reference to null, they can still go ahead.
 * This reference is to be initialized by .onCreate. Due to the way results work, .onResume
 * isn't guaranteed to be called before stuff is cleared. Meh!
 */
public @interface UserOf {
}
