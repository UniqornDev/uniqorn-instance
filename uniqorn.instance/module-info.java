module uniqorn.instance
{
	requires aeonics.boot;
	requires uniqorn;
	requires aeonics.core;
	requires aeonics.http;
	
	provides aeonics.Plugin with local.Main;
}
