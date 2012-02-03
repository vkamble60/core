package org.jboss.as.console.client.core.bootstrap;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.gwtplatform.mvp.client.proxy.PlaceManager;
import com.gwtplatform.mvp.client.proxy.PlaceRequest;
import com.gwtplatform.mvp.client.proxy.TokenFormatter;
import org.jboss.as.console.client.core.BootstrapContext;
import org.jboss.as.console.client.core.NameTokens;
import org.jboss.as.console.client.shared.dispatch.AsyncCommand;

import java.util.List;

/**
 *
 * Either loads the default place or one specified from external context (URL tokens)
 *
 * @author Heiko Braun
 * @date 12/7/11
 */
public class LoadMainApp implements AsyncCommand<Boolean> {

    private PlaceManager placeManager;
    private TokenFormatter formatter;
    private BootstrapContext bootstrapContext;

    public LoadMainApp(BootstrapContext bootstrapContext, PlaceManager placeManager, TokenFormatter formatter) {
        this.bootstrapContext = bootstrapContext;
        this.placeManager = placeManager;
        this.formatter = formatter;
    }

    @Override
    public void execute(AsyncCallback<Boolean> callback) {

        String initialToken = History.getToken();

        if(!initialToken.isEmpty() && !initialToken.equals(NameTokens.SettingsPresenter))
        {
            List<PlaceRequest> hierarchy = formatter.toPlaceRequestHierarchy(initialToken);
            final PlaceRequest placeRequest = hierarchy.get(hierarchy.size() - 1);

            Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
                @Override
                public void execute() {
                    placeManager.revealPlace(placeRequest, true);
                }
            });

            bootstrapContext.setInitialPlace(placeRequest.getNameToken());

        }
        else {
            placeManager.revealDefaultPlace();
        }

        callback.onSuccess(Boolean.TRUE);
    }
}