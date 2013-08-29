<?php
function getHeaders($method, $url)
{

    date_default_timezone_set('UTC');

    $key = "f9aaf865-b89a-444d-9070-38ec6666e539";
    $sec = "9e13e4ac-ad93-4c8f-a896-d5a937b84c8a";

    $date = date('D, j M Y h:i:s ') . 'GMT';
    $preHash = "$method\n$date\n$url";
    $sig = base64_encode(hash_hmac('sha1', $preHash, $sec, TRUE));

    $headers = array(
        'Date: ' . $date,
        'Authorization: TPK ' . $key . ':' . $sig
    );

    return $headers;
}

function callAPI($method, $base, $path, $data = false)
{
    $curl = curl_init();

    $url = sprintf("%s%s", $base, $path);
    switch ($method) {
        case "POST":
            curl_setopt($curl, CURLOPT_POST, 1);
            if ($data) curl_setopt($curl, CURLOPT_POSTFIELDS, $data);
            break;
        case "PUT":
            curl_setopt($curl, CURLOPT_PUT, 1);
            break;
        case "DELETE":
            curl_setopt($curl, CURLOPT_CUSTOMREQUEST, "DELETE");
            break;
        default:
            if ($data) $url = sprintf("%s%s?%s", $base, $path, http_build_query($data));
    }

    print("calling " . $url . "\n");

    #curl_setopt($curl, CURLOPT_VERBOSE, true);
    curl_setopt($curl, CURLOPT_HTTPHEADER, getHeaders($method, $path));
    curl_setopt($curl, CURLOPT_URL, $url);
    curl_setopt($curl, CURLOPT_RETURNTRANSFER, 1);

    return curl_exec($curl);
}

print(callAPI("GET", "http://localhost:9669", "/1/create/views/items") . "\n");
print(callAPI("GET", "http://localhost:9669", "/1/update/views/theitem") . "\n");
print(callAPI("GET", "http://localhost:9669", "/1/query/views", array( 'count' => 10 )) . "\n");

print(callAPI("DELETE", "http://localhost:9669", "/1/clear/views") . "\n");
print(callAPI("DELETE", "http://localhost:9669", "/1/delete/views") . "\n");
?>
