// Compiled by ClojureScript 1.10.520 {}
goog.provide('jibit.core');
goog.require('cljs.core');
goog.require('goog.dom');
goog.require('reagent.core');
goog.require('re_frame.core');
re_frame.core.reg_event_db.call(null,new cljs.core.Keyword(null,"initialize","initialize",609952913),(function (db,_){
if(cljs.core.truth_(new cljs.core.Keyword(null,"init-done","init-done",850010).cljs$core$IFn$_invoke$arity$1(db))){
return db;
} else {
return new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"hello","hello",-245025397),"world",new cljs.core.Keyword(null,"images-query","images-query",-1621219276),cljs.core.PersistentVector.EMPTY,new cljs.core.Keyword(null,"init-done","init-done",850010),true], null);
}
}));
re_frame.core.reg_event_db.call(null,new cljs.core.Keyword(null,"hello","hello",-245025397),(function (db,_){
return cljs.core.update.call(null,db,new cljs.core.Keyword(null,"hello","hello",-245025397),cljs.core.str,"s");
}));
re_frame.core.reg_sub.call(null,new cljs.core.Keyword(null,"hello","hello",-245025397),(function (db,_){
return new cljs.core.Keyword(null,"hello","hello",-245025397).cljs$core$IFn$_invoke$arity$1(db);
}));
re_frame.core.reg_sub.call(null,new cljs.core.Keyword(null,"everything","everything",-1542889541),(function (db,_){
return db;
}));
jibit.core.db_debug_textarea = (function jibit$core$db_debug_textarea(message){
var world = re_frame.core.subscribe.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"everything","everything",-1542889541)], null));
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"textarea","textarea",-650375824),new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"style","style",-496642736),new cljs.core.PersistentArrayMap(null, 4, [new cljs.core.Keyword(null,"background-color","background-color",570434026),"#abc",new cljs.core.Keyword(null,"color","color",1011675173),"#def",new cljs.core.Keyword(null,"width","width",-384071477),"67em",new cljs.core.Keyword(null,"height","height",1025178622),"20em"], null),new cljs.core.Keyword(null,"value","value",305978217),[cljs.core.str.cljs$core$IFn$_invoke$arity$1(message)," ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(cljs.core.deref.call(null,world))].join('')], null)], null);
});
jibit.core.ui = (function jibit$core$ui(){
var value = re_frame.core.subscribe.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"hello","hello",-245025397)], null));
return new cljs.core.PersistentVector(null, 3, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"div","div",1057191632),new cljs.core.PersistentVector(null, 4, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"h1","h1",-1896887462),new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"on-click","on-click",1632826543),((function (value){
return (function (){
return re_frame.core.dispatch.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"hello","hello",-245025397)], null));
});})(value))
], null),"Hello ",cljs.core.deref.call(null,value)], null),new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [jibit.core.db_debug_textarea," xx"], null)], null);
});
jibit.core.get_app_element = (function jibit$core$get_app_element(){
return goog.dom.getElement("app");
});
jibit.core.mount = (function jibit$core$mount(el){
return reagent.core.render_component.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [jibit.core.ui], null),el);
});
jibit.core.mount_app_element = (function jibit$core$mount_app_element(){
var temp__5457__auto__ = jibit.core.get_app_element.call(null);
if(cljs.core.truth_(temp__5457__auto__)){
var el = temp__5457__auto__;
re_frame.core.dispatch_sync.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"initialize","initialize",609952913)], null));

return jibit.core.mount.call(null,el);
} else {
return null;
}
});
jibit.core.mount_app_element.call(null);
jibit.core.on_reload = (function jibit$core$on_reload(){
return jibit.core.mount_app_element.call(null);
});

//# sourceMappingURL=core.js.map
