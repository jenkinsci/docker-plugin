package com.nirima.jenkins.plugins.docker;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.Functions;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.Messages;

/**
 * Records that the user has disabled something "until further notice", or the
 * system has disabled something for a period, or both.
 */
public class DockerDisabled extends AbstractDescribableImpl<DockerDisabled> implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean disabledByChoice;

    private transient boolean disabledBySystem;
    private transient long nanotimeWhenDisabledBySystem;
    private transient long nanotimeWhenReEnableBySystem;
    private transient String reasonWhyDisabledBySystem;
    private transient Throwable exceptionWhenDisabledBySystem;

    // Persistence functionality

    @DataBoundConstructor
    public DockerDisabled() {
    }

    @DataBoundSetter
    public void setDisabledByChoice(final boolean disabledByChoice) {
        this.disabledByChoice = disabledByChoice;
    }

    public boolean getDisabledByChoice() {
        return disabledByChoice;
    }

    // Internal use functionality

    /**
     * Called from owning classes to record a problem that will cause
     * {@link #isDisabled()} to return true for a period.
     * 
     * @param reasonGiven
     *            Human-readable String stating why.
     * @param durationInMilliseconds
     *            Length of time, in milliseconds, the disablement should
     *            continue.
     * @param exception
     *            Optional exception.
     */
    @Restricted(NoExternalUse.class)
    public void disableBySystem(@Nonnull final String reasonGiven, final long durationInMilliseconds,
            @Nullable final Throwable exception) {
        final long durationInNanoseconds = TimeUnit.MILLISECONDS.toNanos(durationInMilliseconds);
        final long now = readTimeNowInNanoseconds();
        disabledBySystem = true;
        nanotimeWhenDisabledBySystem = now;
        nanotimeWhenReEnableBySystem = now + durationInNanoseconds;
        reasonWhyDisabledBySystem = reasonGiven;
        exceptionWhenDisabledBySystem = exception;
    }

    /**
     * Indicates if we are currently disabled for any reason (either the user
     * has ticked the disable box or
     * {@link #disableBySystem(String, long, Throwable)} has been called
     * recently).
     * 
     * @return true if we are currently disabled.
     */
    @Restricted(NoExternalUse.class)
    public boolean isDisabled() {
        return getDisabledByChoice() || getDisabledBySystem();
    }

    // WebUI access methods

    @DataBoundSetter
    public void setEnabledByChoice(final boolean enabledByChoice) {
        setDisabledByChoice(!enabledByChoice);
    }

    public boolean getEnabledByChoice() {
        return !getDisabledByChoice();
    }

    public boolean getDisabledBySystem() {
        if (disabledBySystem) {
            final long now = readTimeNowInNanoseconds();
            final long disabledTimeRemaining = nanotimeWhenReEnableBySystem - now;
            if (disabledTimeRemaining > 0) {
                return true;
            }
            disabledBySystem = false;
            nanotimeWhenDisabledBySystem = 0L;
            nanotimeWhenReEnableBySystem = 0L;
            reasonWhyDisabledBySystem = null;
            exceptionWhenDisabledBySystem = null;
        }
        return false;
    }

    /** How long ago this was disabled by the system, e.g. "3 min 0 sec". */
    public String getWhenDisabledBySystemString() {
        if (!getDisabledBySystem()) {
            return "";
        }
        final long now = readTimeNowInNanoseconds();
        final long howLongAgoInNanoseconds = now - nanotimeWhenDisabledBySystem;
        final long howLongAgoInMilliseconds = TimeUnit.NANOSECONDS.toMillis(howLongAgoInNanoseconds);
        return Util.getPastTimeString(howLongAgoInMilliseconds);
    }

    /**
     * How long ago this will remain disabled by the system, e.g. "2 min 0 sec".
     */
    public String getWhenReEnableBySystemString() {
        final long now = readTimeNowInNanoseconds();
        if (!getDisabledBySystem()) {
            return "";
        }
        final long howSoonInNanoseconds = nanotimeWhenReEnableBySystem - now;
        final long howSoonInMilliseconds = TimeUnit.NANOSECONDS.toMillis(howSoonInNanoseconds);
        return Util.getTimeSpanString(howSoonInMilliseconds);
    }

    public String getReasonWhyDisabledBySystem() {
        if (!getDisabledBySystem()) {
            return "";
        }
        return reasonWhyDisabledBySystem;
    }

    public String getExceptionWhenDisabledBySystemString() {
        if (!getDisabledBySystem() || exceptionWhenDisabledBySystem == null) {
            return "";
        }
        return Functions.printThrowable(exceptionWhenDisabledBySystem);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DockerDisabled> {
        public FormValidation doCheckEnabledByChoice(@QueryParameter boolean enabledByChoice,
                @QueryParameter boolean disabledBySystem, @QueryParameter String whenDisabledBySystemString,
                @QueryParameter String whenReEnableBySystemString, @QueryParameter String reasonWhyDisabledBySystem,
                @QueryParameter String exceptionWhenDisabledBySystemString) {
            if (!enabledByChoice) {
                return FormValidation.warning("Note: Disabled.");
            }
            if (disabledBySystem) {
                final String reason = Util.fixNull(reasonWhyDisabledBySystem);
                final String disabledAgo = Util.fixNull(whenDisabledBySystemString);
                final String enableWhen = Util.fixNull(whenReEnableBySystemString);
                final String exception = Util.fixNull(exceptionWhenDisabledBySystemString);
                if (!reason.isEmpty() && !disabledAgo.isEmpty() && !enableWhen.isEmpty()) {
                    final StringBuilder html = new StringBuilder();
                    html.append("Note: Disabled ");
                    html.append(Util.escape(disabledAgo));
                    html.append(" ago due to error.");
                    html.append("  Will re-enable in ");
                    html.append(Util.escape(enableWhen));
                    html.append(".");
                    html.append("<br/>Reason: ");
                    html.append(Util.escape(reason));
                    if (!exception.isEmpty()) {
                        html.append(" <a href='#' class='showDetails'>");
                        html.append(Messages.FormValidation_Error_Details());
                        html.append("</a><pre style='display:none'>");
                        html.append(Util.escape(exception));
                        html.append("</pre>");
                    }
                    return FormValidation.warningWithMarkup(html.toString());
                }
            }
            return FormValidation.ok();
        }
    }

    // Basic Java Object methods

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DockerDisabled other = (DockerDisabled) o;
        if (disabledByChoice != other.disabledByChoice) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = disabledByChoice ? 1 : 0;
        return result;
    }

    @Override
    public String toString() {
        final boolean ByChoice = getDisabledByChoice();
        final boolean bySystem = getDisabledBySystem();
        if (bySystem) {
            final String ago = getWhenDisabledBySystemString();
            final String until = getWhenReEnableBySystemString();
            final String why = getReasonWhyDisabledBySystem();
            if (ByChoice) {
                return "ByChoice,BySystem," + ago + "," + until + "," + why;
            }
            return "BySystem," + ago + "," + until + "," + why;
        }
        if (ByChoice) {
            return "ByChoice";
        }
        return "No";
    }

    // Test accessor
    @Restricted(NoExternalUse.class)
    protected long readTimeNowInNanoseconds() {
        return System.nanoTime();
    }
}
