package io.jenkins.docker.client;

import com.github.dockerjava.core.AbstractDockerCmdExecFactory;
import com.github.dockerjava.netty.NettyDockerCmdExecFactory;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper class to allow compatibility between 3.1 and 3.2 APIs
 *
 * @author bguerin
 */
public class NettyDockerCmdExecFactoryCompat extends NettyDockerCmdExecFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerAPI.class);

    public NettyDockerCmdExecFactoryCompat withConnectTimeoutCompat(Integer connectTimeout) {
        Method method = getMethod(NettyDockerCmdExecFactory.class, "withConnectTimeout");
        if (method == null) {
            method = getMethod(AbstractDockerCmdExecFactory.class, "withConnectTimeout");
        }

        invoke(method, connectTimeout, "withConnectTimeout");

        return this;
    }

    public NettyDockerCmdExecFactoryCompat withReadTimeoutCompat(Integer readTimeout) {
        Method method = getMethod(NettyDockerCmdExecFactory.class, "withReadTimeout");
        if (method == null) {
            method = getMethod(AbstractDockerCmdExecFactory.class, "withReadTimeout");
        }

        invoke(method, readTimeout, "withReadTimeout");

        return this;
    }

    private Method getMethod(Class<?> clazz, String name) {
        try {
            return clazz.getMethod(name, Integer.class);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    private void invoke(Method method, Integer arg, String name) {
        if (method != null) {
            try {
                method.invoke(this, arg);
            } catch (Exception ex) {
                LOGGER.error("Error invoking method {} on *DockerCmdExecFactory", name, ex);
            }
        } else {
            LOGGER.error("Could not find method {} on *DockerCmdExecFactory", name);
        }
    }
}
