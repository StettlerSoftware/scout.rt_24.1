package org.eclipse.scout.rt.client.mobile.transformation;

import org.eclipse.scout.rt.client.extension.ui.desktop.outline.pages.AbstractPageWithTableExtension;
import org.eclipse.scout.rt.client.extension.ui.desktop.outline.pages.PageChains.PageInitPageChain;
import org.eclipse.scout.rt.client.ui.basic.table.ITable;
import org.eclipse.scout.rt.client.ui.desktop.outline.pages.AbstractPageWithTable;
import org.eclipse.scout.rt.platform.BEANS;
import org.eclipse.scout.rt.shared.ui.UserAgentUtility;

public class MobilePageWithTableExtension extends AbstractPageWithTableExtension<ITable, AbstractPageWithTable<ITable>> {

  public MobilePageWithTableExtension(AbstractPageWithTable<ITable> owner) {
    super(owner);
  }

  @Override
  public void execInitPage(PageInitPageChain chain) {
    super.execInitPage(chain);
    if (!UserAgentUtility.isMobileDevice()) {
      return;
    }
    BEANS.get(IDeviceTransformationService.class).getDeviceTransformer().transformPage(getOwner());
  }

}
