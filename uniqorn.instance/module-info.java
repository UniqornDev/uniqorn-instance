module uniqorn.instance
{
	requires aeonics.boot;
	requires uniqorn;
	requires aeonics.core;
	requires aeonics.http;
	requires aeonics.git;
	requires aeonics.mcp;

	provides aeonics.Plugin with local.Main;
}
