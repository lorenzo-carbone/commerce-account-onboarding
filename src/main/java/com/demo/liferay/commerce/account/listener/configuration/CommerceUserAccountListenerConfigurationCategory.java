package com.demo.liferay.commerce.account.listener.configuration;

import com.liferay.configuration.admin.category.ConfigurationCategory;
import org.osgi.service.component.annotations.Component;

/**
 * Add a new section 'Demo' and a new category 'Commerce Forms Listeners' under System Settings.
 */
@Component(
    immediate = true,
    service = ConfigurationCategory.class
)
public class CommerceUserAccountListenerConfigurationCategory
    implements ConfigurationCategory {

  @Override
  public String getCategoryKey() {

    return CATEGORY_KEY;
  }

  @Override
  public String getCategorySection() {

    return CATEGORY_SECTION;
  }

  public static final String CATEGORY_KEY = "commerce-forms-listeners";
  public static final String CATEGORY_SECTION = "demo";

}
