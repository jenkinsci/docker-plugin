package io.jenkins.docker.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.netty.MediaType;
import com.github.dockerjava.netty.WebTarget;
import com.github.dockerjava.netty.exec.AbstrSyncDockerCmdExec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Bugfixed version of docker-java's ListContainersCmdExec. This version doesn't
 * rely on Jersey's FiltersEncoder so it works when all you've got is Netty. It
 * also removes the logging, as Netty's WebTarget class has no useful toString()
 * method, rendering the output largely useless.
 */
public class ListContainersCmdExec extends AbstrSyncDockerCmdExec<ListContainersCmd, List<Container>> implements
        ListContainersCmd.Exec {

    private static final Logger LOGGER = LoggerFactory.getLogger(ListContainersCmdExec.class);

    public ListContainersCmdExec(WebTarget baseResource, DockerClientConfig dockerClientConfig) {
        super(baseResource, dockerClientConfig);
    }

    @Override
    protected List<Container> execute(ListContainersCmd command) {
        WebTarget webTarget = getBaseResource().path("/containers/json").queryParam("since", command.getSinceId())
                .queryParam("before", command.getBeforeId());

        webTarget = booleanQueryParam(webTarget, "all", command.hasShowAllEnabled());
        webTarget = booleanQueryParam(webTarget, "size", command.hasShowSizeEnabled());

        if (command.getLimit() != null && command.getLimit() >= 0) {
            webTarget = webTarget.queryParam("limit", String.valueOf(command.getLimit()));
        }

        if (command.getFilters() != null && !command.getFilters().isEmpty()) {
            // BugFix starts here
            // Old code used FiltersEncoder.jsonEncode(command.getFilters())
            final ObjectMapper objectMapper = new ObjectMapper();
            final String encodedFilters;
            try {
                encodedFilters = objectMapper.writeValueAsString(command.getFilters());
            } catch (JsonProcessingException ex) {
                throw new RuntimeException(ex);
            }
            webTarget = webTarget.queryParam("filters", encodedFilters);
            // End BugFix
        }

        List<Container> containers = webTarget.request().accept(MediaType.APPLICATION_JSON)
                .get(new TypeReference<List<Container>>() {
                });

        return containers;
    }

}
