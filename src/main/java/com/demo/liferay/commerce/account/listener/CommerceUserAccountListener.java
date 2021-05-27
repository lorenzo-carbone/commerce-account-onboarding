package com.demo.liferay.commerce.account.listener;

import com.demo.liferay.commerce.account.listener.configuration.CommerceUserAccountListenerConfiguration;
import com.liferay.commerce.account.constants.CommerceAccountConstants;
import com.liferay.commerce.account.model.CommerceAccount;
import com.liferay.commerce.account.model.CommerceAccountUserRel;
import com.liferay.commerce.account.service.CommerceAccountLocalService;
import com.liferay.commerce.account.service.CommerceAccountUserRelLocalService;
import com.liferay.dynamic.data.mapping.model.DDMFormInstanceRecordVersion;
import com.liferay.dynamic.data.mapping.storage.DDMFormFieldValue;
import com.liferay.dynamic.data.mapping.storage.DDMFormValues;
import com.liferay.portal.configuration.metatype.bnd.util.ConfigurableUtil;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.exception.ModelListenerException;
import com.liferay.portal.kernel.model.BaseModelListener;
import com.liferay.portal.kernel.model.ModelListener;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.search.SortFactoryUtil;
import com.liferay.portal.kernel.service.*;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Liferay Form Listener to automatically create user accounts who filled a dedicated form.
 */
@Component(
        immediate = true,
        configurationPid = CommerceUserAccountListenerConfiguration.PID,
        service = ModelListener.class
)
public class CommerceUserAccountListener
        extends BaseModelListener<DDMFormInstanceRecordVersion> {

  private final static String FIRSTNAME = "firstName";
  private final static String LASTNAME = "lastName";
  private final static String EMAIL = "email";
  private final static String COMPANY_NAME = "companyName";
  private final static String VAT_NUMBER = "vatNumber";

  /**
   * Listen to form records updates and add or update a new user depending on the form ID and the
   * form's workflow status.
   *
   * @param formRecord a form submission
   * @throws ModelListenerException if the form record cannot be updated
   * @see CommerceUserAccountListenerConfiguration
   */
  @Override
  public void onAfterUpdate(DDMFormInstanceRecordVersion formRecord)
          throws ModelListenerException {

    long formId = _config.formId();

    _log.debug("The expected form ID from the configuration is {}", formId);
    _log.debug("The actual form ID here is {}", formRecord.getFormInstanceId());

    if (formId == formRecord.getFormInstanceId() &&
            WorkflowConstants.STATUS_PENDING == formRecord.getStatus()) {
      addInactiveUser(formRecord);
    }

    if (formId == formRecord.getFormInstanceId() &&
            WorkflowConstants.STATUS_APPROVED == formRecord.getStatus()) {
      activateUser(formRecord);
    }

    super.onAfterUpdate(formRecord);
  }


  /**
   * Add a user with the status 'inactive' when a form to create an account is submitted for
   * publication.
   *
   * @param formRecord a form submission
   */
  private void addInactiveUser(DDMFormInstanceRecordVersion formRecord) {

    _log.debug("Entering addInactiveUser() method");
    try {

      Locale locale = formRecord.getDDMForm().getDefaultLocale();

      String firstName = "";
      String lastName = "";
      String email = "";
      String companyName = "";
      String vatNumber = "";
      String screenName = "";

      ServiceContext serviceContext = ServiceContextThreadLocal.getServiceContext();

      DDMFormValues ddmFormValues = formRecord.getDDMFormValues();
      Map<String, List<DDMFormFieldValue>> ddmFormFieldValuesReferencesMap = ddmFormValues.getDDMFormFieldValuesReferencesMap(true);

      email = ddmFormFieldValuesReferencesMap.get(EMAIL).get(0).getValue().getString(locale);
      firstName = ddmFormFieldValuesReferencesMap.get(FIRSTNAME).get(0).getValue().getString(locale);
      lastName = ddmFormFieldValuesReferencesMap.get(LASTNAME).get(0).getValue().getString(locale);
      companyName = ddmFormFieldValuesReferencesMap.get(COMPANY_NAME).get(0).getValue().getString(locale);
      vatNumber = ddmFormFieldValuesReferencesMap.get(VAT_NUMBER).get(0).getValue().getString(locale);
      screenName = firstName + "." + lastName;

      User newUser = _userLocalService.addUser(
              _companyLocalService.getCompany(formRecord.getCompanyId()).getDefaultUser().getUserId(),
              formRecord.getCompanyId(), true, null, null,
              false, screenName, email,
              -1L, null, locale,
              firstName, "", lastName, -1L, -1L, false,
              1, 1, 1970, null, null,
              null, null, null, false, null
      );

      newUser.setStatus(WorkflowConstants.STATUS_INACTIVE);
      _userLocalService.updateUser(newUser);

      CommerceAccount commerceAccount = _commerceAccountLocalService.addBusinessCommerceAccount(companyName,
              CommerceAccountConstants.DEFAULT_PARENT_ACCOUNT_ID, email,
              vatNumber, false, null,null, null, serviceContext);

      List<CommerceAccountUserRel> commerceAccountUserRels = commerceAccount.getCommerceAccountUserRels();

      _commerceAccountLocalService.updateCommerceAccount(commerceAccount);

      Role role = _roleLocalService.getRole(
              serviceContext.getCompanyId(),
              CommerceAccountConstants.ROLE_NAME_ACCOUNT_ADMINISTRATOR);

      CommerceAccountUserRel commerceAccountUserRel = _commerceAccountUserRelLocalService
              .addCommerceAccountUserRel(commerceAccount.getCommerceAccountId(), newUser.getUserId(), new long[] {role.getRoleId()}, serviceContext);

      commerceAccountUserRels.add(commerceAccountUserRel);

      _commerceAccountUserRelLocalService.updateCommerceAccountUserRel(commerceAccountUserRel);

      _log.debug("New pending user from user account creation form:");
      _log.debug(newUser.toString());

    } catch (Exception e) {
      _log.error("Something's wrong, I can feel it", e);
    }
  }

  /**
   * Update an inactive user with the status 'active' if the form submission has been approved.
   *
   * @param formRecord a form submission
   */
  private void activateUser(DDMFormInstanceRecordVersion formRecord) {

    _log.debug("Entering activateUser() method");

    String email = "";
    String companyName = "";

    try {

      Locale locale = formRecord.getDDMForm().getDefaultLocale();

      ServiceContext serviceContext = ServiceContextThreadLocal.getServiceContext();

      DDMFormValues ddmFormValues = formRecord.getDDMFormValues();
      Map<String, List<DDMFormFieldValue>> ddmFormFieldValuesReferencesMap = ddmFormValues.getDDMFormFieldValuesReferencesMap(true);

      email = ddmFormFieldValuesReferencesMap.get(EMAIL).get(0).getValue().getString(locale);
      companyName = ddmFormFieldValuesReferencesMap.get(COMPANY_NAME).get(0).getValue().getString(locale);

      User user = _userLocalService
              .getUserByEmailAddress(formRecord.getCompanyId(), email);
      user.setStatus(WorkflowConstants.STATUS_APPROVED);
      _userLocalService.updateUser(user);

      List<CommerceAccount> commerceAccounts = _commerceAccountLocalService.searchCommerceAccounts(serviceContext.getCompanyId(),
              0, companyName, CommerceAccountConstants.ACCOUNT_TYPE_BUSINESS,
              false, QueryUtil.ALL_POS, QueryUtil.ALL_POS, SortFactoryUtil.create("name", false));

      for (CommerceAccount commerceAccount : commerceAccounts) {
        if(StringUtil.equals(companyName, commerceAccount.getName())){
          commerceAccounts.get(0).setActive(true);
          _commerceAccountLocalService.updateCommerceAccount(commerceAccounts.get(0));
          break;
        }
      }

    } catch (Exception e) {
      _log.error("Something's wrong, I can feel it", e);
    }
  }

  @Activate
  @Modified
  public void activate(Map<String, String> properties) {

    _config = ConfigurableUtil
            .createConfigurable(CommerceUserAccountListenerConfiguration.class, properties);
  }

  @Reference
  private CompanyLocalService _companyLocalService;

  @Reference
  private UserLocalService _userLocalService;

  @Reference
  private CommerceAccountLocalService _commerceAccountLocalService;

  @Reference
  private CommerceAccountUserRelLocalService _commerceAccountUserRelLocalService;

  @Reference
  private RoleLocalService _roleLocalService;

  private volatile CommerceUserAccountListenerConfiguration _config;
  private static final Logger _log = LoggerFactory.getLogger(CommerceUserAccountListener.class);
}
