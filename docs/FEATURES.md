# Features

Chronological list of every feature and improvement. Maintained with the `feature-documentator` skill.

* App: Added the welcome screen, in English and French, with the Monica light and dark theme.
* App: Added the instance sheet to choose the Monica server, kept on the device.
* App: Gave the dark theme a night-blue palette with blue buttons, a white logo and a moonlit panda on the welcome screen.
* Instance sheet: An address is now checked against the instance before it is saved, with a clear message when it cannot be used.
* Instance sheet: Release builds only accept instances served over HTTPS.
* Sign in: Added logging in through the instance's own sign-in page in the browser, on the official instance or a self-hosted one.
* Sign up: Added signing up through the instance's registration page, or a message when the instance accepts no new accounts.
* Home: Added a signed-in home showing the person and the instance.
* Session: The session stays signed in across restarts, renews itself silently and survives network loss.
* Session: The app explains when a session expired or was ended from the web.
* Sign out: Added signing out, which ends this phone's session on the instance, even when it was offline.
* Instance sheet: Switching instance while signed in asks first and signs out of the previous instance.
* Vaults: Signing in or creating an account now lands on the list of all your vaults, in the order arranged on the web.
* Vaults: Each vault shows its colour, its initial, its name, its number of contacts and whether it is the default vault.
* Vaults: Added the empty state for an account with no vault, with a button to create the first one.
* Vaults: The list reloads when pulled down, or from the Refresh action of TalkBack and Switch Access.
* Vaults: Added creating a vault with a name, a description and a colour, showing the instance's message when it refuses.
* Vaults: Tapping a vault opens a first vault screen with its name and description.
* Account: The person's initials open a sheet with the instance settings and signing out, replacing the placeholder home.
* Vaults: Vault colours stay readable in the dark theme.
* Vaults: The list of vaults and each vault stay readable without a connection, from a copy kept on the phone.
* Vaults: The list appears at once when the app opens, then updates from the instance.
* Vaults: A line says when what is shown is not up to date, and since when.
* Privacy: The copy kept on the phone is encrypted, never backed up, and erased at sign-out, instance switch, session end, or after 30 days without reaching the instance.
