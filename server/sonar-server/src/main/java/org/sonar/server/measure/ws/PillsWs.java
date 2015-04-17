package org.sonar.server.measure.ws;

import org.apache.commons.io.IOUtils;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.RequestHandler;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.web.UserRole;
import org.sonar.core.measure.db.MeasureDto;
import org.sonar.core.persistence.DbSession;
import org.sonar.server.db.DbClient;
import org.sonar.server.user.UserSession;

import java.io.InputStream;

public class PillsWs implements WebService, RequestHandler {

  static final String PARAM_PROJECT_KEY = "projectKey";

  private final DbClient db;

  public PillsWs(DbClient db) {
    this.db = db;
  }

  @Override
  public void define(Context context) {
    NewController newController = context.createController("api/pills");
    NewAction qGateAction = newController.createAction("qualitygate");
    qGateAction.setSince("5.2");
    qGateAction.createParam(PARAM_PROJECT_KEY).setRequired(true);
    qGateAction.setHandler(this);
    newController.done();
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    String projectKey = request.mandatoryParam(PARAM_PROJECT_KEY);
    UserSession.get().checkProjectPermission(UserRole.USER, projectKey);

    DbSession dbSession = db.openSession(false);
    MeasureDto measure = db.measureDao().findByComponentKeyAndMetricKey(dbSession, projectKey, CoreMetrics.ALERT_STATUS_KEY);
    Response.Stream stream = response.stream();
    stream.setMediaType("image/svg+xml");
    InputStream img;
    if (measure != null) {
      img = getClass().getResourceAsStream("/org/sonar/server/measure/ws/pills/qualitygate-passed.svg");
    } else {
      img = getClass().getResourceAsStream("/org/sonar/server/measure/ws/pills/qualitygate-na.svg");
    }
    IOUtils.copy(img, stream.output());
  }
}
