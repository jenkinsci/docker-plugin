package com.nirima.jenkins.plugins.docker.utils;

import com.google.common.base.Splitter;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Node;
import hudson.model.Run;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import io.jenkins.docker.DockerTransientNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilities to fetch things out of jenkins environment.
 */
@Restricted(NoExternalUse.class)
public class JenkinsUtils {
    private static final Logger LOG = LoggerFactory.getLogger(JenkinsUtils.class);
    private static String _id;

    /**
     * If the build was on a docker cloud, get the cloud.
     *
     * @param build The build under inspection.
     * @return {@link Optional} containing the {@link DockerCloud} or otherwise
     *         {@link Optional#empty()}.
     */
    @Restricted(NoExternalUse.class)
    public static Optional<DockerCloud> getCloudForBuild(AbstractBuild build) {
        Node node = build.getBuiltOn();
        if (node instanceof DockerTransientNode) {
            return Optional.of(((DockerTransientNode) node).getCloud());
        }
        return Optional.empty();
    }

    /**
     * If the build was workflow, get the ID of that channel.
     *
     * @param channel From the build under inspection.
     * @return {@link Optional} containing the {@link DockerCloud} or otherwise
     *         {@link Optional#empty()}.
     */
    @Restricted(NoExternalUse.class)
    public static Optional<DockerCloud> getCloudForChannel(VirtualChannel channel) {
        if (channel instanceof Channel) {
            Channel c = (Channel) channel;
            Node node = Jenkins.get().getNode(c.getName());
            if (node instanceof DockerTransientNode) {
                return Optional.of(((DockerTransientNode) node).getCloud());
            }
        }
        return Optional.empty();
    }

    @Restricted(NoExternalUse.class)
    public static Optional<DockerCloud> getCloudThatWeBuiltOn(Run<?, ?> build, Launcher launcher) {
        Optional<DockerCloud> cloud;
        // A bit unpleasant, but the getBuiltOn method is in AbstractBuild and
        // we may be a workflow run.
        if (build instanceof AbstractBuild) {
            cloud = JenkinsUtils.getCloudForBuild((AbstractBuild) build);
        } else {
            cloud = JenkinsUtils.getCloudForChannel(launcher.getChannel());
        }
        return cloud;
    }

    /**
     * Finds the {@link DockerCloud} with the {@link DockerCloud#getDisplayName()}
     * matching the specified name.
     *
     * @param serverName The name to look for.
     * @return {@link DockerCloud} with the {@link DockerCloud#getDisplayName()}
     *         matching the specified name.
     * @throws IllegalArgumentException if no {@link DockerCloud} exists with that
     *                                  name.
     */
    @Restricted(NoExternalUse.class)
    @NonNull
    public static DockerCloud getCloudByNameOrThrow(final String serverName) {
        try {
            final DockerCloud resultOrNull = DockerCloud.getCloudByName(serverName);
            if (resultOrNull != null) {
                return resultOrNull;
            }
        } catch (ClassCastException treatedAsCloudNotFound) {
            // we found a cloud of that name, but it wasn't one of ours.
        }
        throw new IllegalArgumentException("No " + DockerCloud.class.getSimpleName() + " with name '" + serverName
                + "'.  Known names are " + getServerNames());
    }

    @Restricted(NoExternalUse.class)
    @NonNull
    public static List<String> getServerNames() {
        return DockerCloud.instances().stream()
                .map(cloud -> cloud == null ? "" : cloud.getDisplayName())
                .collect(Collectors.toList());
    }

    @Restricted(NoExternalUse.class)
    @NonNull
    public static String getInstanceId() {
        try {
            if (_id == null) {
                _id = Util.getDigestOf(new ByteArrayInputStream(
                        InstanceIdentity.get().getPublic().getEncoded()));
            }
        } catch (IOException e) {
            LOG.error("Could not get Jenkins instance ID.", e);
            _id = "";
        }
        return _id;
    }

    @Restricted(NoExternalUse.class)
    public static void setTestInstanceId(final String id) {
        _id = id;
    }

    /**
     * returns the Java system property specified by <code>key</code>. If that
     * fails, a default value is returned instead.
     * <p>
     * To be replaced with jenkins.util.SystemProperties.getString() once they lift
     * their @Restricted(NoExternalUse.class)
     *
     * @param key          the key of the system property to read.
     * @param defaultValue the default value which shall be returned in case the
     *                     property is not defined.
     * @return the system property of <code>key</code>, or <code>defaultValue</code>
     *         in case the property is not defined.
     */
    @Restricted(NoExternalUse.class)
    public static String getSystemPropertyString(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    /**
     * returns the Java system property specified by <code>key</code>. If that
     * fails, a default value is returned instead.
     * <p>
     * In case the value of the system property cannot be parsed properly (e.g. a
     * character was passed, causing a parsing error to occur), the default value is
     * returned.
     * <p>
     * To be replaced with jenkins.util.SystemProperties.getLong() once they lift
     * their @Restricted(NoExternalUse.class)
     *
     * @param key          the key of the system property to read.
     * @param defaultValue the default value which shall be returned in case the
     *                     property is not defined.
     * @return the system property of <code>key</code>, or <code>defaultValue</code>
     *         in case the property is not defined.
     */
    @Restricted(NoExternalUse.class)
    public static Long getSystemPropertyLong(String key, Long defaultValue) {
        String value = getSystemPropertyString(key, null);
        if (value == null) {
            return defaultValue;
        }
        Long longValue = null;
        try {
            longValue = Long.decode(value);
        } catch (NumberFormatException e) {
            LOG.warn(
                    "System property {} is attempted to be read as type Long, but value '{}' cannot be parsed as a number",
                    key,
                    value,
                    e);
            return defaultValue;
        }
        return longValue;
    }

    /**
     * returns the Java system property specified by <code>key</code>. If that
     * fails, a default value is returned instead.
     * <p>
     * In case the value of the system property cannot be parsed properly (e.g. an
     * invalid identifier was passed), the value <code>false</code> is returned.
     * <p>
     * To be replaced with jenkins.util.SystemProperties.getBoolean() once they lift
     * their @Restricted(NoExternalUse.class)
     *
     * @param key          the key of the system property to read.
     * @param defaultValue the default value which shall be returned in case the
     *                     property is not defined.
     * @return the system property of <code>key</code>, or <code>defaultValue</code>
     *         in case the property is not defined.
     */
    @Restricted(NoExternalUse.class)
    public static boolean getSystemPropertyBoolean(String key, boolean defaultValue) {
        String value = getSystemPropertyString(key, null);
        if (value == null) {
            return defaultValue;
        }
        boolean booleanValue = false;
        booleanValue = Boolean.parseBoolean(value);
        return booleanValue;
    }

    /**
     * Turns empty arrays into nulls.
     *
     * @param       <T> Any kind of {@link Object}.
     * @param array The array (or null)
     * @return null or the non-empty array.
     */
    @Restricted(NoExternalUse.class)
    @CheckForNull
    public static <T> T[] fixEmpty(@Nullable T[] array) {
        if (array == null || array.length == 0) {
            return null;
        }
        return array;
    }

    /**
     * Turns empty collections into nulls.
     *
     * @param            <C> Any kind of {@link Collection}.
     * @param collection The collection (or null)
     * @return null or the non-empty collection.
     */
    @Restricted(NoExternalUse.class)
    @CheckForNull
    public static <C extends Collection> C fixEmpty(@Nullable C collection) {
        if (collection == null || collection.isEmpty()) {
            return null;
        }
        return collection;
    }

    /**
     * Turns empty maps into nulls.
     *
     * @param     <C> Any kind of {@link Map}.
     * @param map The map (or null)
     * @return null or the non-empty map.
     */
    @Restricted(NoExternalUse.class)
    @CheckForNull
    public static <C extends Map<?, ?>> C fixEmpty(@Nullable C map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        return map;
    }

    /**
     * Used to help with toString methods: Starts a new toString result.
     *
     * @param subjectOfToString The class being turned into a string.
     * @return A {@link StringBuilder} containing the class's name.
     */
    @Restricted(NoExternalUse.class)
    public static StringBuilder startToString(Object subjectOfToString) {
        return new StringBuilder(subjectOfToString.getClass().getSimpleName());
    }

    /**
     * Used to help with toString methods: Ends a toString result.
     *
     * @param sb The {@link StringBuilder} from {@link #startToString(Object)}.
     */
    @Restricted(NoExternalUse.class)
    public static void endToString(StringBuilder sb) {
        if (sb.indexOf("{") >= 0) {
            sb.append("}");
        } else {
            sb.append("{}");
        }
    }

    /**
     * Used to help with toString methods: Appends to a toString result.
     *
     * @param               <T> The type being appended.
     * @param sb            The {@link StringBuilder} from
     *                      {@link #startToString(Object)}.
     * @param attributeName The field name
     * @param value         The field value
     */
    @Restricted(NoExternalUse.class)
    public static <T> void bldToString(StringBuilder sb, String attributeName, @Nullable T[] value) {
        if (value == null) {
            return;
        }
        if (sb.indexOf("{") >= 0) {
            sb.append(", ");
        } else {
            sb.append("{");
        }
        sb.append(attributeName);
        sb.append("=");
        sb.append(Arrays.toString(value));
    }

    /**
     * Used to help with toString methods: Appends to a toString result.
     *
     * @param sb            The {@link StringBuilder} from
     *                      {@link #startToString(Object)}.
     * @param attributeName The field name
     * @param value         The field value
     */
    @Restricted(NoExternalUse.class)
    public static void bldToString(StringBuilder sb, String attributeName, int value) {
        if (sb.indexOf("{") >= 0) {
            sb.append(", ");
        } else {
            sb.append("{");
        }
        sb.append(attributeName);
        sb.append("=");
        sb.append(value);
    }

    /**
     * Used to help with toString methods: Appends to a toString result.
     *
     * @param sb            The {@link StringBuilder} from
     *                      {@link #startToString(Object)}.
     * @param attributeName The field name
     * @param value         The field value
     */
    @Restricted(NoExternalUse.class)
    public static void bldToString(StringBuilder sb, String attributeName, @Nullable Object value) {
        if (value == null) {
            return;
        }
        if (sb.indexOf("{") >= 0) {
            sb.append(", ");
        } else {
            sb.append("{");
        }
        sb.append(attributeName);
        sb.append("=");
        if (value instanceof String) {
            sb.append('\'').append(value).append('\'');
        } else {
            sb.append(value);
        }
    }

    /**
     * Splits a (potentially null/empty) text string into an array of non-empty
     * strings.
     *
     * @param s         The string to be split.
     * @param separator The separator regex, e.g. "\n".
     * @return An array (possibly empty, never null).
     */
    @Restricted(NoExternalUse.class)
    @NonNull
    public static String[] splitAndFilterEmpty(@Nullable String s, String separator) {
        if (s == null) {
            return new String[0];
        }
        final List<String> result = new ArrayList<>();
        for (String o : Splitter.on(separator).omitEmptyStrings().split(s)) {
            result.add(o);
        }
        return result.toArray(new String[0]);
    }

    /**
     * Splits a (potentially null/empty) text string into a {@link List} of
     * non-empty strings.
     *
     * @param s         The string to be split.
     * @param separator The separator regex, e.g. "\n".
     * @return A {@link List} (possibly empty, never null).
     */
    @Restricted(NoExternalUse.class)
    @NonNull
    public static List<String> splitAndFilterEmptyList(@Nullable String s, String separator) {
        final List<String> result = new ArrayList<>();
        if (s != null) {
            for (String o : Splitter.on(separator).omitEmptyStrings().split(s)) {
                result.add(o);
            }
        }
        return result;
    }

    /**
     * Splits a (potentially null/empty) text string into a {@link List} of
     * non-empty strings, trimming each entry too.
     *
     * @param s         The string to be split.
     * @param separator The separator regex, e.g. "\n".
     * @return A {@link List} (possibly empty, never null).
     */
    public static List<String> splitAndTrimFilterEmptyList(String s, String separator) {
        final List<String> result = new ArrayList<>();
        if (s != null) {
            for (String o :
                    Splitter.on(separator).omitEmptyStrings().trimResults().split(s)) {
                result.add(o);
            }
        }
        return result;
    }

    /**
     * Splits a (potentially null/empty) text string of the form
     * "name=value&lt;separator&gt;foo=bar" into a {@link Map}, ignoring empty
     * sections and sections that do not include an "=".
     *
     * @param s         The string to be split.
     * @param separator The separator regex, e.g. "\n".
     * @return A {@link Map} (possibly empty, never null).
     */
    @Restricted(NoExternalUse.class)
    @NonNull
    public static Map<String, String> splitAndFilterEmptyMap(@Nullable String s, String separator) {
        final Map<String, String> result = new LinkedHashMap<>();
        if (s != null) {
            for (String o : Splitter.on(separator).omitEmptyStrings().split(s)) {
                String[] parts = o.trim().split("=", 2);
                if (parts.length == 2) {
                    result.put(parts[0].trim(), parts[1].trim());
                }
            }
        }
        return result;
    }

    /**
     * Clones a String array but stripping all entries and omitting any that are
     * null or empty after stripping.
     *
     * @param arr The starting array; this will not be modified.
     * @return A new array no longer than the one given, but which may be empty.
     */
    @Restricted(NoExternalUse.class)
    @NonNull
    public static String[] filterStringArray(@Nullable String[] arr) {
        final ArrayList<String> strings = new ArrayList<>();
        if (arr != null) {
            for (String s : arr) {
                s = StringUtils.stripToNull(s);
                if (s != null) {
                    strings.add(s);
                }
            }
        }
        return strings.toArray(new String[0]);
    }

    /**
     * Makes a copy of a {@link List} of Jenkins objects. This is effectively a deep
     * clone. Typically used to copy something that's been configured in a template
     * before it's used in something generated from that template.
     *
     * @param <T>        The type of thing to be copied.
     * @param listOrNull The list of things to be copied.
     * @return A deep clone of the list.
     */
    @Restricted(NoExternalUse.class)
    public static <T> List<T> makeCopyOfList(@Nullable List<? extends T> listOrNull) {
        if (listOrNull == null) {
            return null;
        }
        final List<T> copyList = new ArrayList<>(listOrNull.size());
        for (final T originalElement : listOrNull) {
            final T copyOfElement = makeCopy(originalElement);
            copyList.add(copyOfElement);
        }
        return copyList;
    }

    /**
     * Makes a copy of a Jenkins object. This is effectively a deep clone. Typically
     * used to copy something that's been configured in a template before it's used
     * in something generated from that template.
     *
     * @param <T>      The type of thing to be copied.
     * @param original The thing to be copied.
     * @return A deep clone of the thing.
     */
    @Restricted(NoExternalUse.class)
    public static <T> T makeCopy(@Nullable final T original) {
        if (original == null) {
            return null;
        }
        final String xml = Jenkins.XSTREAM.toXML(original);
        final Object copy = Jenkins.XSTREAM.fromXML(xml);
        return (T) copy;
    }
}
