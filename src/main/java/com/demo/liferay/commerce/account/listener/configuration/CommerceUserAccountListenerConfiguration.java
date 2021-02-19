package com.demo.liferay.commerce.account.listener.configuration;

import aQute.bnd.annotation.metatype.Meta;
import com.liferay.portal.configuration.metatype.annotations.ExtendedObjectClassDefinition;

@ExtendedObjectClassDefinition(
    category = CommerceUserAccountListenerConfigurationCategory.CATEGORY_KEY,
    scope = ExtendedObjectClassDefinition.Scope.SYSTEM
)
@Meta.OCD(
    id = CommerceUserAccountListenerConfiguration.PID,
    localization = "content/Language",
    name = "commerce-form-user-account-listener-config-name"
)
public interface CommerceUserAccountListenerConfiguration {

  @Meta.AD(
      name = "commerce-form-user-account-listener-form-id",
      description = "You can find this information after creating a form under 'Content & Data > Forms' in a site",
      required = false
  )
  public long formId();

  public static final String PID = "com.demo.liferay.commerce.account.listener.configuration.CommerceUserAccountListenerConfiguration";

}
