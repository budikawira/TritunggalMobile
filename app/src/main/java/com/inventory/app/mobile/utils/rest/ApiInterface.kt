package com.inventory.app.mobile.utils.rest

import com.inventory.app.mobile.utils.rest.requests.GetEpcByStickerNoRequest
import com.inventory.app.mobile.utils.rest.requests.GetItemByEpcRequest
import com.inventory.app.mobile.utils.rest.requests.GetItemByLocationRequest
import com.inventory.app.mobile.utils.rest.requests.GetLocationsRequest
import com.inventory.app.mobile.utils.rest.requests.GetMasterItemRequest
import com.inventory.app.mobile.utils.rest.requests.PlacementCreateRequest
import com.inventory.app.mobile.utils.rest.requests.SignInRequest
import com.inventory.app.mobile.utils.rest.requests.RegisterRfidRequest
import com.inventory.app.mobile.utils.rest.requests.ShipmentInitRequest
import com.inventory.app.mobile.utils.rest.requests.StockOpnameUploadRequest
import com.inventory.app.mobile.utils.rest.requests.TransferConfirmRequest
import com.inventory.app.mobile.utils.rest.requests.TransferInitRequest
import com.inventory.app.mobile.utils.rest.requests.TransferUploadRequest
import com.inventory.app.mobile.utils.rest.response.BaseObjectResponse
import com.inventory.app.mobile.utils.rest.response.BaseResponse
import com.inventory.app.mobile.utils.rest.response.BaseTableResponse
import com.inventory.app.mobile.utils.rest.response.GetItemByEpcResponse
import com.inventory.app.mobile.utils.rest.response.GetItemByLocationResponse
import com.inventory.app.mobile.utils.rest.response.GetLocationsResponse
import com.inventory.app.mobile.utils.rest.response.GetMasterItemResponse
import com.inventory.app.mobile.utils.rest.response.SignInResponse
import com.inventory.app.mobile.utils.rest.response.ProductResult
import com.inventory.app.mobile.utils.rest.response.RegisterRfidResponse
import com.inventory.app.mobile.utils.rest.response.ShipmentInitResponse
import com.inventory.app.mobile.utils.rest.response.ShipmentListInitResponse
import com.inventory.app.mobile.utils.rest.response.StockOpnameUploadResponse
import com.inventory.app.mobile.utils.rest.response.TransferInitResponse
import com.inventory.app.mobile.utils.rest.response.TransferListInitResponse
import com.inventory.app.mobile.utils.rest.response.TransferUploadResponse
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiInterface {
    @Headers("Accept: application/json")
    @POST("api/Auth/SignIn")
    fun login(@Body request: SignInRequest): Call<SignInResponse?>

    @Headers("Accept: application/json")
    @POST("api/Rfid/Register")
    fun registerRfid(
        @Header("Authorization") jwtToken: String,
        @Body request: RegisterRfidRequest): Call<RegisterRfidResponse?>

    @Headers("Accept: application/json")
    @POST("api/Rfid/GetItemByEpc")
    fun getItemByEpc(
        @Header("Authorization") jwtToken: String,
        @Body request: GetItemByEpcRequest): Call<GetItemByEpcResponse?>

    @Headers("Accept: application/json")
    @POST("api/Rfid/GetEpcByStickerNo")
    fun getEpcByStickerNo(@Body request: GetEpcByStickerNoRequest): Call<BaseObjectResponse<String>?>

    @Headers("Accept: application/json")
    @POST("api/Inv/TransferListInit")
    fun transferListInit(
        @Header("Authorization") jwtToken: String): Call<TransferListInitResponse?>

    @Headers("Accept: application/json")
    @POST("api/Inv/TransferInit")
    fun transferInit(
        @Header("Authorization") jwtToken: String,
        @Body request: TransferInitRequest): Call<TransferInitResponse?>

    @Headers("Accept: application/json")
    @POST("api/Inv/TransferUpload")
    fun transferUpload(
        @Header("Authorization") jwtToken: String,
        @Body request: TransferUploadRequest): Call<TransferUploadResponse?>

    @Headers("Accept: application/json")
    @POST("api/Inv/TransferConfirmOut")
    fun transferConfirmOut(
        @Header("Authorization") jwtToken: String,
        @Body request: TransferConfirmRequest
    ): Call<BaseResponse?>


    @Headers("Accept: application/json")
    @POST("api/Inv/PlacementListInit")
    fun placementListInit(
        @Header("Authorization") jwtToken: String): Call<TransferListInitResponse?>

    @Headers("Accept: application/json")
    @POST("api/Inv/PlacementInit")
    fun placementInit(
        @Header("Authorization") jwtToken: String,
        @Body request: TransferInitRequest): Call<TransferInitResponse?>

    @Headers("Accept: application/json")
    @POST("api/Inv/PlacementUpload")
    fun placementUpload(
        @Header("Authorization") jwtToken: String,
        @Body request: TransferUploadRequest): Call<TransferUploadResponse?>

    @Headers("Accept: application/json")
    @POST("api/Inv/PlacementConfirm")
    fun placementConfirm(
        @Header("Authorization") jwtToken: String,
        @Body request: TransferConfirmRequest
    ): Call<BaseResponse?>

    @Headers("Accept: application/json")
    @POST("api/Inv/PlacementCreate")
    fun placementCreate(
        @Header("Authorization") jwtToken: String,
        @Body request: PlacementCreateRequest
    ): Call<BaseResponse?>

    @Headers("Accept: application/json")
    @POST("api/Inv/ShipmentCreate")
    fun shipmentCreate(
        @Header("Authorization") jwtToken: String,
        @Body request: PlacementCreateRequest
    ): Call<BaseResponse?>

    @POST("api/Inv/ShipmentInit")
    fun shipmentInit(
        @Header("Authorization") jwtToken: String,
        @Body request: ShipmentInitRequest
    ): Call<ShipmentInitResponse?>

    @Headers("Accept: application/json")
    @POST("api/Inv/ShipmentListInit")
    fun shipmentListInit(
        @Header("Authorization") jwtToken: String
    ): Call<TransferListInitResponse?>

    @Headers("Accept: application/json")
    @POST("api/Inv/ShipmentUpload")
    fun shipmentUpload(
        @Header("Authorization") jwtToken: String,
        @Body request: TransferUploadRequest
    ): Call<TransferUploadResponse?>

    @Headers("Accept: application/json")
    @POST("api/Inv/ShipmentConfirm")
    fun shipmentConfirm(
        @Header("Authorization") jwtToken: String,
        @Body request: TransferConfirmRequest
    ): Call<BaseResponse?>
    @Headers("Accept: application/json")
    @POST("api/Inv/StockOpnameUpload")
    fun stockOpnameUpload(
        @Header("Authorization") jwtToken: String,
        @Body request: StockOpnameUploadRequest): Call<StockOpnameUploadResponse?>

    @Headers("Accept: application/json")
    @POST("api/Loc/GetLocations")
    fun getLocations(
        @Header("Authorization") jwtToken: String,
        @Body request: GetLocationsRequest): Call<GetLocationsResponse?>

    @Headers("Accept: application/json")
    @POST("api/Inv/GetItemByLocation")
    fun getItemByLocation(
        @Header("Authorization") jwtToken: String,
        @Body request: GetItemByLocationRequest): Call<GetItemByLocationResponse?>

    @Headers("Accept: application/json")
    @POST("api/Inv/GetMasterItem")
    fun getMasterItem(
        @Header("Authorization") jwtToken: String,
        @Body request: GetMasterItemRequest): Call<GetMasterItemResponse?>

}