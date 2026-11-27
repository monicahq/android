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
