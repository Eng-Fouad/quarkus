package io.quarkus.funqy.deployment.bindings.http;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;
import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ExecutorBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.funqy.deployment.FunctionBuildItem;
import io.quarkus.funqy.deployment.FunctionInitializedBuildItem;
import io.quarkus.funqy.runtime.bindings.http.FunqyHttpBindingRecorder;
import io.quarkus.jackson.runtime.ObjectMapperProducer;
import io.quarkus.vertx.core.deployment.CoreVertxBuildItem;
import io.quarkus.vertx.http.deployment.RequireBodyHandlerBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.runtime.VertxHttpBuildTimeConfig;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class FunqyHttpBuildStep {
    public static final String FUNQY_HTTP_FEATURE = "funqy-http";

    @BuildStep
    public void markObjectMapper(BuildProducer<UnremovableBeanBuildItem> unremovable) {
        unremovable.produce(new UnremovableBeanBuildItem(
                new UnremovableBeanBuildItem.BeanClassNameExclusion(ObjectMapper.class.getName())));
        unremovable.produce(new UnremovableBeanBuildItem(
                new UnremovableBeanBuildItem.BeanClassNameExclusion(ObjectMapperProducer.class.getName())));
    }

    @BuildStep
    public RequireBodyHandlerBuildItem requestBodyHandler(List<FunctionBuildItem> functions) {
        if (functions.isEmpty()) {
            return null;
        }
        // Require the body handler if there are functions as they may require the HTTP body
        return new RequireBodyHandlerBuildItem();
    }

    @BuildStep()
    @Record(STATIC_INIT)
    public void staticInit(FunqyHttpBindingRecorder binding,
            BeanContainerBuildItem beanContainer, // dependency
            Optional<FunctionInitializedBuildItem> hasFunctions,
            VertxHttpBuildTimeConfig httpBuildTimeConfig) {
        if (!hasFunctions.isPresent() || hasFunctions.get() == null)
            return;

        // The context path + the resources path
        String rootPath = httpBuildTimeConfig.rootPath();
        binding.init();
    }

    @BuildStep
    @Record(RUNTIME_INIT)
    public void boot(ShutdownContextBuildItem shutdown,
            FunqyHttpBindingRecorder binding,
            BuildProducer<FeatureBuildItem> feature,
            BuildProducer<RouteBuildItem> routes,
            CoreVertxBuildItem vertx,
            Optional<FunctionInitializedBuildItem> hasFunctions,
            List<FunctionBuildItem> functions,
            BeanContainerBuildItem beanContainer,
            VertxHttpBuildTimeConfig httpConfig,
            ExecutorBuildItem executorBuildItem) {

        if (!hasFunctions.isPresent() || hasFunctions.get() == null)
            return;
        feature.produce(new FeatureBuildItem(FUNQY_HTTP_FEATURE));

        String rootPath = httpConfig.rootPath();
        Handler<RoutingContext> handler = binding.start(rootPath,
                vertx.getVertx(),
                shutdown,
                beanContainer.getValue(),
                executorBuildItem.getExecutorProxy());

        for (FunctionBuildItem function : functions) {
            if (rootPath == null)
                rootPath = "/";
            else if (!rootPath.endsWith("/"))
                rootPath += "/";
            String name = function.getFunctionName() == null ? function.getMethodName() : function.getFunctionName();
            //String path = rootPath + name;
            String path = "/" + name;
            routes.produce(RouteBuildItem.builder().route(path).handler(handler).build());
        }
    }
}
