var gapi = {
 "auth2": {"init": function () {},
           "getAuthInstance": function() {},
           "GoogleUser": {"getBasicProfile": function(){},
                          "isSignedIn": function(){}}}
}

gapi.auth2.getAuthInstance.prototype = function () {};
gapi.auth2.getAuthInstance.prototype = {
  "currentUser": {"get": function () {},
                  "listen": function () {}},
  "signOut": function () {},
  "signIn": function () {}};


gapi.auth2.getAuthInstance.currentUser.get.prototype = function () {};
gapi.auth2.getAuthInstance.currentUser.get.prototype = {
  "getAuthResponse": {"id_token": {}}
  }

gapi.auth2.GoogleUser.getBasicProfile.prototype = function () {};
gapi.auth2.GoogleUser.getBasicProfile.prototype = {
  "getName": function(){},
  "getImageUrl": function(){}
}