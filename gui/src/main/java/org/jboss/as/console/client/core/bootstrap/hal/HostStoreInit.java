package org.jboss.as.console.client.core.bootstrap.hal;

import com.allen_sauer.gwt.log.client.Log;
import com.google.inject.Inject;
import org.jboss.as.console.client.core.BootstrapContext;
import org.jboss.as.console.client.domain.model.SimpleCallback;
import org.jboss.as.console.client.v3.stores.domain.HostStore;
import org.jboss.gwt.flow.client.Control;

import java.util.Set;

/**
 * @author Heiko Braun
 * @date 15/07/14
 */
public class HostStoreInit implements BootstrapStep {

    private final HostStore hostStore;

    @Inject
    public HostStoreInit(HostStore hostStore) {
        this.hostStore = hostStore;
    }

    @Override
    public void execute(final Control<BootstrapContext> control) {
        final BootstrapContext context = control.getContext();

        if (!context.isStandalone()) {
            hostStore.init(new SimpleCallback<Set<String>>() {
                @Override
                public void onFailure(Throwable caught) {
                    if (caught instanceof HostStore.NoHostsAvailable) {
                        // this is expected (host scoped roles)
                        context.setHostManagementDisabled(true);
                        control.proceed();
                    } else {
                        context.setlastError(caught);
                        control.abort();
                    }
                }

                @Override
                public void onSuccess(Set<String> hosts) {
                    Log.info("Identified " + hosts.size() + " hosts in this domain");

                    if (hosts.isEmpty()) {
                        context.setHostManagementDisabled(true);
                    }

                    control.proceed();

                }
            });
        } else {
            // standalone
            control.proceed();
        }
    }
}
