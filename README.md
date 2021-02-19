# Liferay Commerce Account Onboarding

Proof of concept demonstrating how
[*Liferay Forms*](https://learn.liferay.com/dxp/7.x/en/process-automation/forms/user-guide/introduction-to-forms.html)
could be used for an Account(Customer) onboarding process using
[`ModelListener`](https://learn.liferay.com/dxp/7.x/en/liferay-internals/extending-liferay/creating-a-model-listener.html?highlight=modellistener) and native Kaleo Workflow Engine.

## Installation

- Deploy this module in your portal instance under `$LIFERAY_HOME/deploy` or `$LIFERAY_HOME/osgi/modules`.

or

- Clone this repo.
 
## Configuration

- [Create a form](https://learn.liferay.com/dxp/7.x/en/process-automation/forms/user-guide/creating-forms.html)
with `companyName`, `vatNumber`, `firstname`, `lastname`, `email` fields.
- [Apply a *Single Approver* workflow to it](https://learn.liferay.com/dxp/7.x/en/process-automation/workflow/user-guide/activating-workflow.html#forms).
- Copy & paste the form ID in `Control Panel > System Settings > Demo - Commerce Forms Listeners`

## Usage

- Make a form submission.
- You should see a new inactive Account and User under `Control Panel > Users > Account` and `Control Panel > Users > Users & Organizations` 
and a notification on your user profile to approve the form.
- The user created is automatically added to the Account with the role `Account Administrator`.
- Approve the form.
- The new User and Account should be active now.

## License

[MIT](LICENSE)
