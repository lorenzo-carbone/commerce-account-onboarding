package com.demo.liferay.commerce.account.listener;

import com.demo.liferay.commerce.account.listener.configuration.CommerceUserAccountListenerConfiguration;
import com.liferay.commerce.account.constants.CommerceAccountConstants;
import com.liferay.commerce.account.model.CommerceAccount;
import com.liferay.commerce.account.model.CommerceAccountUserRel;
import com.liferay.commerce.account.service.CommerceAccountLocalService;
import com.liferay.commerce.account.service.CommerceAccountUserRelLocalService;
import com.liferay.dynamic.data.mapping.model.DDMFormInstanceRecordVersion;
import com.liferay.dynamic.data.mapping.storage.DDMFormFieldValue;
import com.liferay.portal.configuration.metatype.bnd.util.ConfigurableUtil;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.exception.ModelListenerException;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.model.BaseModelListener;
import com.liferay.portal.kernel.model.ModelListener;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.search.SortFactoryUtil;
import com.liferay.portal.kernel.service.*;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import java.util.HashMap;
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

  private final static String FIRSTNAME = "firstname";
  private final static String LASTNAME = "lastname";
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
      Map<String, String> fields = getFormFieldsAsMap(formRecord);

      ServiceContext serviceContext = ServiceContextThreadLocal.getServiceContext();

      User newUser = _userLocalService.addUser(
          _companyLocalService.getCompany(formRecord.getCompanyId()).getDefaultUser().getUserId(),
          formRecord.getCompanyId(), true, null, null,
          true, null, fields.getOrDefault(EMAIL, ""),
          -1L, null, formRecord.getDDMForm().getDefaultLocale(),
          fields.getOrDefault(FIRSTNAME, ""), fields.getOrDefault("middlename", ""),
          fields.getOrDefault(LASTNAME, ""), -1L, -1L, false,
          1, 1, 1970, null, null,
          null, null, null, false, null
      );

      newUser.setStatus(WorkflowConstants.STATUS_INACTIVE);
      _userLocalService.updateUser(newUser);

      CommerceAccount commerceAccount = _commerceAccountLocalService.addBusinessCommerceAccount(fields.get(COMPANY_NAME), CommerceAccountConstants.DEFAULT_PARENT_ACCOUNT_ID, fields.get(EMAIL),
              fields.get(VAT_NUMBER), false, null,null, null,serviceContext);

      List<CommerceAccountUserRel> commerceAccountUserRels = commerceAccount.getCommerceAccountUserRels();


      _commerceAccountLocalService.updateCommerceAccount(commerceAccount);

      Role role = _roleLocalService.getRole(
              serviceContext.getCompanyId(),
              CommerceAccountConstants.ROLE_NAME_ACCOUNT_ADMINISTRATOR);

      CommerceAccountUserRel commerceAccountUserRel = _commerceAccountUserRelLocalService.addCommerceAccountUserRel(commerceAccount.getCommerceAccountId(), newUser.getUserId(), new long[] {role.getRoleId()}, serviceContext);

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
    try {

      ServiceContext serviceContext = ServiceContextThreadLocal.getServiceContext();

      Map<String, String> fields = getFormFieldsAsMap(formRecord);

      User user = _userLocalService
          .getUserByEmailAddress(formRecord.getCompanyId(), fields.get(EMAIL));
      user.setStatus(WorkflowConstants.STATUS_APPROVED);
      _userLocalService.updateUser(user);

      List<CommerceAccount> commerceAccounts = _commerceAccountLocalService.searchCommerceAccounts(serviceContext.getCompanyId(),
              0, fields.get(COMPANY_NAME), CommerceAccountConstants.ACCOUNT_TYPE_BUSINESS,
              false, QueryUtil.ALL_POS, QueryUtil.ALL_POS, SortFactoryUtil.create("name", false));

      for (CommerceAccount commerceAccount : commerceAccounts) {
        if(StringUtil.equals(fields.get(COMPANY_NAME), commerceAccount.getName())){
          commerceAccounts.get(0).setActive(true);
          _commerceAccountLocalService.updateCommerceAccount(commerceAccounts.get(0));
          break;
        }
      }

    } catch (Exception e) {
      _log.error("Something's wrong, I can feel it", e);
    }
  }

  /**
   * Transform form names and values from a form record to a map.
   *
   * @param formRecord a form submission
   * @return a map with fields name as key and fields value as value
   * @throws PortalException if the form values can't be parsed properly
   */
  private Map<String, String> getFormFieldsAsMap(DDMFormInstanceRecordVersion formRecord)
      throws PortalException {

    Locale locale = formRecord.getDDMForm().getDefaultLocale();
    List<DDMFormFieldValue> fields = formRecord.getDDMFormValues().getDDMFormFieldValues();
    Map<String, String> formFields = new HashMap<>();

    _log.debug("Transform form fields to map (id={})", formRecord.getFormInstanceId());

    fields.forEach(ddmFormFieldValue -> {

      String key = ddmFormFieldValue.getName().toLowerCase();
      String value = ddmFormFieldValue.getValue().getString(locale);

      _log.debug("Field -> {}[{}]={}", key, ddmFormFieldValue.getType(), value);

      formFields.put(key, value);

    });
    return formFields;
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
