package org.nuxeo.opensocial.container.factory;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.shindig.gadgets.spec.View;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.spaces.api.Gadget;
import org.nuxeo.opensocial.container.client.bean.GadgetBean;
import org.nuxeo.opensocial.container.client.bean.GadgetPosition;
import org.nuxeo.opensocial.container.client.bean.GadgetView;
import org.nuxeo.opensocial.container.factory.utils.GadgetsUtils;
import org.nuxeo.opensocial.container.factory.utils.UrlBuilder;

/**
 * @author Guillaume Cusnieux
 */
public class GadgetFactory {

  public static GadgetBean getGadgetBean(Gadget gadget, boolean permission)
      throws ClientException {
    GadgetBean bean = new GadgetBean();
    bean.setCollapsed(gadget.isCollapsed());
    bean.setHeight(gadget.getHeight());
    bean.setHtmlContent(gadget.getHtmlContent());
    bean.setPosition(new GadgetPosition(gadget.getPlaceId(),
        gadget.getPosition()));
    bean.setRenderUrl(UrlBuilder.buildShindigUrl(gadget, permission));
    bean.setTitle(getTitle(gadget));
    bean.setUserPrefs(PreferenceManager.getPreferences(gadget));
    bean.setDefaultPrefs(PreferenceManager.getDefaultPreferences(gadget));
    bean.setGadgetViews(createGadgetViews(gadget));
    bean.setName(gadget.getName());
    bean.setPermission(permission);
    bean.setRef(gadget.getId());
    bean.setViewer(gadget.getViewer());
    return bean;
  }

  private static String getTitle(Gadget gadget) throws ClientException {
    if (gadget.getTitle() != null && !gadget.getTitle()
        .equals(""))
      return _getTitleWithoutKey(gadget.getTitle());
    else
      return _getTitleWithoutKey(gadget.getName());

  }

  private static String _getTitleWithoutKey(String title) {
    StringTokenizer st = new StringTokenizer(title, ".");
    return st.nextToken();
  }

  private static Map<String, GadgetView> createGadgetViews(Gadget gadget) {
    Map<String, GadgetView> gv = new HashMap<String, GadgetView>();
    try {
      Map<String, View> views = GadgetsUtils.getGadgetSpec(gadget)
          .getViews();
      for (String v : views.keySet()) {
        View view = views.get(v);
        gv.put(v, new GadgetView(view.getName(), view.getType()
            .toString()));
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return gv;
  }

  public static Gadget getGadget(GadgetBean bean) throws ClientException {
    return new GadgetAdapter(bean);
  }

}
