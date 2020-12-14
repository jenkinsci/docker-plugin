package com.nirima.jenkins.plugins.docker.action;

import com.github.dockerjava.api.DockerClient;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import hudson.model.Action;

/**
 * Action to record launching of an agent.
 */
public class DockerLaunchAction implements Action, Serializable {
    private static final long serialVersionUID = 322300594612029744L;

    public static class Item {
        public final DockerClient client;
        public final String id;

        public Item(DockerClient client, String id) {
            this.client = client;
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final Item item = (Item) o;
            if (!client.equals(item.client)) return false;
            if (!id.equals(item.id)) return false;
            return true;
        }

        @Override
        public int hashCode() {
            int result = client.hashCode();
            result = 31 * result + id.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "Item [client=" + client + ", id=" + id + "]";
        }
    }

    private transient List<Item> running = new ArrayList<>();

    /**
     * Initializes data structure that we don't persist.
     */
    private Object readResolve() {
        running = new ArrayList<>();
        return this;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return null;
    }

    public void started(DockerClient client, String containerName) {
        running.add( new Item(client, containerName) );
    }

    public void stopped(DockerClient client, String containerName) {
        running.remove( new Item(client, containerName) );
    }

    public Iterable<Item> getRunning() {
        return running;
    }
}
