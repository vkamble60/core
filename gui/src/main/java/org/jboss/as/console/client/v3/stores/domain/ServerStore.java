package org.jboss.as.console.client.v3.stores.domain;

import com.google.gwt.user.client.rpc.AsyncCallback;
import org.jboss.as.console.client.Console;
import org.jboss.as.console.client.domain.model.HostInformationStore;
import org.jboss.as.console.client.domain.model.Server;
import org.jboss.as.console.client.domain.model.SimpleCallback;
import org.jboss.as.console.client.shared.model.ModelAdapter;
import org.jboss.as.console.client.shared.util.DMRUtil;
import org.jboss.as.console.client.v3.stores.domain.actions.AddServer;
import org.jboss.as.console.client.v3.stores.domain.actions.CopyServer;
import org.jboss.as.console.client.v3.stores.domain.actions.HostSelection;
import org.jboss.as.console.client.v3.stores.domain.actions.RefreshServer;
import org.jboss.as.console.client.v3.stores.domain.actions.RemoveServer;
import org.jboss.as.console.client.v3.stores.domain.actions.UpdateServer;
import org.jboss.as.console.client.widgets.forms.ApplicationMetaData;
import org.jboss.as.console.client.widgets.forms.PropertyBinding;
import org.jboss.dmr.client.ModelDescriptionConstants;
import org.jboss.dmr.client.ModelNode;
import org.jboss.dmr.client.dispatch.DispatchAsync;
import org.jboss.dmr.client.dispatch.impl.DMRAction;
import org.jboss.dmr.client.dispatch.impl.DMRResponse;
import org.jboss.gwt.circuit.ChangeSupport;
import org.jboss.gwt.circuit.Dispatcher;
import org.jboss.gwt.circuit.meta.Process;
import org.jboss.gwt.circuit.meta.Store;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jboss.dmr.client.ModelDescriptionConstants.*;
import static org.jboss.dmr.client.ModelDescriptionConstants.STEPS;

/**
 * @author Heiko Braun
 * @date 15/07/14
 */
@Store
public class ServerStore extends ChangeSupport {

    private final DispatchAsync dispatcher;
    private final ApplicationMetaData propertyMetaData;
    private HostInformationStore hostInfo;

    private HostStore hostStore;

    private Map<String, List<Server>> serverModel = new HashMap<>();
    private Server selectedServer;

    @Inject
    public ServerStore(HostStore hostStore, HostInformationStore hostInfo, DispatchAsync dispatcher, ApplicationMetaData propertyMetaData) {
        this.hostStore = hostStore;
        this.hostInfo = hostInfo;
        this.dispatcher = dispatcher;
        this.propertyMetaData = propertyMetaData;
    }

    public void init(final String hostName, final AsyncCallback<List<Server>> callback) {
        hostInfo.getServerConfigurations(hostName, new SimpleCallback<List<Server>>() {
            @Override
            public void onSuccess(List<Server> servers) {
                ServerStore.this.serverModel.put(hostName, servers);
                selectedServer = servers.get(0);
                callback.onSuccess(servers);
            }
        });
    }

    @Process(actionType = HostSelection.class, dependencies = {HostStore.class})
    public void onSelectHost(String hostName, final Dispatcher.Channel channel) {

        onRefresh(channel);

    }

    @Process(actionType = RefreshServer.class, dependencies = {HostStore.class})
    public void onRefresh(final Dispatcher.Channel channel) {

        final String hostName = hostStore.getSelectedHost();

        hostInfo.getServerConfigurations(hostName, new SimpleCallback<List<Server>>() {
            @Override
            public void onSuccess(List<Server> servers) {
                serverModel.put(hostName, servers);
                channel.ack();
                fireChanged(ServerStore.class);
            }

            @Override
            public void onFailure(Throwable caught) {
                channel.nack(caught);
            }
        });

    }

    @Process(actionType = AddServer.class)
    public void onAddServer(final Server server, final Dispatcher.Channel channel) {

        hostInfo.createServerConfig(hostStore.getSelectedHost(), server, new SimpleCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean success) {

                String selectedHost = hostStore.getSelectedHost();

                if(!serverModel.containsKey(selectedHost))
                    serverModel.put(selectedHost, new ArrayList<Server>());
                serverModel.get(selectedHost).add(server);

                channel.ack();
                fireChanged(ServerStore.class);
            }

            @Override
            public void onFailure(Throwable caught) {
                Console.error("Failed to add server", caught.getMessage());
                channel.nack(caught);
            }
        });

    }

    @Process(actionType = RemoveServer.class)
    public void onRemoveServer(final Server server, final Dispatcher.Channel channel) {

        hostInfo.deleteServerConfig(hostStore.getSelectedHost(), server, new SimpleCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean success) {
                boolean removed = ServerStore.this.serverModel.get(hostStore.getSelectedHost()).remove(server);
                if(!removed)
                    throw new RuntimeException("Failed to remove server");

                channel.ack();
                fireChanged(ServerStore.class);
            }

            @Override
            public void onFailure(Throwable caught) {
                channel.nack(caught);
            }
        });
    }

    @Process(actionType = UpdateServer.class)
    public void onUpdateServer(final UpdateServer.Values update, final Dispatcher.Channel channel) {

        final Map<String, Object> changedValues = update.getChangedValues();
        Server entity = update.getServer();

        if (changedValues.containsKey("portOffset")) { changedValues.put("socketBinding", entity.getSocketBinding()); }

        if (changedValues.containsKey("socketBinding")) { changedValues.put("portOffset", entity.getPortOffset()); }

        final String name = entity.getName();

        ModelNode proto = new ModelNode();
        proto.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        proto.get(ADDRESS).add("host", hostStore.getSelectedHost());
        proto.get(ADDRESS).add(ModelDescriptionConstants.SERVER_CONFIG, name);

        List<PropertyBinding> bindings = propertyMetaData.getBindingsForType(Server.class);
        ModelNode operation = ModelAdapter.detypedFromChangeset(proto, changedValues, bindings);

        // TODO: https://issues.jboss.org/browse/AS7-3643

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = result.get();

                if (response.isFailure()) {
                    Console.error(Console.MESSAGES.modificationFailed("Server Configuration ") + name,
                            response.getFailureDescription());

                } else {
                    Console.info(Console.MESSAGES.modified("Server Configuration ") + name);
                }

                onRefresh(channel);
            }

            @Override
            public void onFailure(Throwable caught) {
                channel.nack(caught);
            }
        });
    }

    @Process(actionType = CopyServer.class)
    public void onSaveCopy(CopyServer.Values copyValues, final Dispatcher.Channel channel) {

        final Server original = copyValues.getOriginal();
        final Server newServer = copyValues.getNewServer();
        final String targetHost = copyValues.getTargetHost();

        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(ADDRESS).setEmptyList();
        operation.get(ADDRESS).add("host", hostStore.getSelectedHost());
        operation.get(ADDRESS).add("server-config", original.getName());
        operation.get(RECURSIVE).set(true);

        dispatcher.execute(new DMRAction(operation, false), new AsyncCallback<DMRResponse>() {
            @Override
            public void onFailure(Throwable caught) {
                Console.error("Failed to read server-config: " + original.getName(), caught.getMessage());
                channel.nack(caught);
            }

            @Override
            public void onSuccess(DMRResponse result) {

                ModelNode response = result.get();

                if (response.isFailure()) {
                    Console.error("Failed to read server-config: " + original.getName(), response.getFailureDescription());
                } else {
                    ModelNode model = response.get("result").asObject();

                    // required attribute changes: portOffset & serverGroup
                    model.get("socket-binding-port-offset").set(newServer.getPortOffset());
                    model.remove("name");

                    // re-create node

                    ModelNode compositeOp = new ModelNode();
                    compositeOp.get(OP).set(COMPOSITE);
                    compositeOp.get(ADDRESS).setEmptyList();

                    List<ModelNode> steps = new ArrayList<ModelNode>();

                    final ModelNode rootResourceOp = new ModelNode();
                    rootResourceOp.get(OP).set(ADD);
                    rootResourceOp.get(ADDRESS).add("host", targetHost);
                    rootResourceOp.get(ADDRESS).add("server-config", newServer.getName());

                    steps.add(rootResourceOp);

                    DMRUtil.copyResourceValues(model, rootResourceOp, steps);

                    compositeOp.get(STEPS).set(steps);

                    dispatcher.execute(new DMRAction(compositeOp), new SimpleCallback<DMRResponse>() {
                        @Override
                        public void onSuccess(DMRResponse dmrResponse) {
                            ModelNode response = dmrResponse.get();

                            if (response.isFailure()) {
                                Console.error("Failed to copy server-config", response.getFailureDescription());
                            } else {
                                Console.info("Successfully copied server-config '" + newServer.getName() + "'");
                            }

                            onRefresh(channel);
                        }
                    });

                }

            }

        });


    }

    // data access

    public List<Server> getServerModel(String host) {
        List<Server> servers = serverModel.get(host);
        return servers != null ? servers : Collections.<Server>emptyList();
    }

    public String getSelectedServer() {
        return selectedServer.getName();
    }

    public Server getSelectedServerInstance() {
        return selectedServer;
    }
}